pipeline {
    agent any
    
    parameters {
        choice(
            name: 'ACTION',
            choices: ['Infrastructure Configuration', 'Application Deployment', 'Stop running Server', 'Display Addresses', 'Destroy Infrastructure', 'Start Server'],
            description: 'Select an action to perform on the cloud infrastructure'
        )
    }
    
    stages {
        stage('Check Instance States') {
            steps {
                sh '''
                    set +x
                    echo "=== Checking Instance States ==="
                    
                    # Get instance states directly from cloud provider
                    # For AWS example:
                    aws ec2 describe-instances --query 'Reservations[].Instances[].[InstanceId,State.Name]' --output json > instance_states.json
                    
                    # Parse the results and write to properties file
                    python3 -c '
import json
import os

# Read instance states
with open("instance_states.json", "r") as f:
    instances = json.load(f)

# Check states
has_instances = len(instances) > 0
has_running = any(instance[1] == "running" for instance in instances)
has_stopped = any(instance[1] == "stopped" for instance in instances)

# Write to properties file
with open("serverState.properties", "w") as f:
    f.write(f"hasInstances={str(has_instances).lower()}\\n")
    f.write(f"hasRunningInstances={str(has_running).lower()}\\n")
    f.write(f"hasStoppedInstances={str(has_stopped).lower()}\\n")
    
    # Write instance details
    if has_instances:
        f.write("instanceIds=")
        instance_ids = [instance[0] for instance in instances]
        f.write(",".join(instance_ids) + "\\n")
    '
                    
                    echo "=== Instance States Saved ==="
                '''
                
                script {
                    // Load the properties file
                    if (fileExists('serverState.properties')) {
                        props = readProperties file: 'serverState.properties'
                        echo "Current server state:"
                        echo "- Has instances: ${props.hasInstances}"
                        echo "- Has running instances: ${props.hasRunningInstances}"
                        echo "- Has stopped instances: ${props.hasStoppedInstances}"
                        
                        // Store the properties in environment variables for later stages
                        env.HAS_INSTANCES = props.hasInstances
                        env.HAS_RUNNING_INSTANCES = props.hasRunningInstances
                        env.HAS_STOPPED_INSTANCES = props.hasStoppedInstances
                        if (props.instanceIds) {
                            env.INSTANCE_IDS = props.instanceIds
                        }
                    } else {
                        error "Failed to create or read serverState.properties"
                    }
                }
            }
        }
        
        stage('Display Server State') {
            steps {
                sh '''
                    set +x
                    echo "=== Server State ==="
                    if [ "${HAS_INSTANCES}" = "true" ]; then
                        echo "Instances found:"
                        # For AWS example:
                        aws ec2 describe-instances --query 'Reservations[].Instances[].[InstanceId,State.Name,PublicIpAddress,Tags[?Key==`Name`].Value | [0]]' --output table
                    else
                        echo "No instances found."
                    fi
                    echo "==================="
                '''
                
                script {
                    def selectedAction = params.ACTION
                    echo "Selected action: ${selectedAction}"
                    
                    // Determine valid actions based on server state
                    def validActions = []
                    if (env.HAS_RUNNING_INSTANCES == 'true') {
                        validActions = ['Application Deployment', 'Stop running Server', 'Display Addresses', 'Destroy Infrastructure']
                    } else if (env.HAS_STOPPED_INSTANCES == 'true') {
                        validActions = ['Start Server', 'Destroy Infrastructure']
                    } else {
                        validActions = ['Infrastructure Configuration']
                    }
                    
                    // Always allow Infrastructure Configuration if we want to create new instances
                    if (!validActions.contains('Infrastructure Configuration')) {
                        validActions.add('Infrastructure Configuration')
                    }
                    
                    echo "Valid actions for current state:"
                    validActions.each { action ->
                        echo "- ${action}"
                    }
                    
                    // Validate selected action
                    if (!selectedAction || !validActions.contains(selectedAction)) {
                        error "❌ The selected action '${selectedAction}' is not valid for the current server state. Please select one of: ${validActions.join(', ')}"
                    }
                    
                    // Store valid actions for later stages
                    env.VALID_ACTIONS = validActions.join(',')
                }
            }
        }
        
        stage('Parameter Validation') {
            steps {
                script {
                    echo "Validating parameters for action: ${params.ACTION}"
                    
                    // Specific validations for each action type
                    switch(params.ACTION) {
                        case 'Infrastructure Configuration':
                            // No additional parameters needed
                            break
                            
                        case 'Application Deployment':
                            if (env.HAS_RUNNING_INSTANCES != 'true') {
                                error "Cannot deploy application: No running instances available"
                            }
                            break
                            
                        case 'Stop running Server':
                            if (env.HAS_RUNNING_INSTANCES != 'true') {
                                error "Cannot stop server: No running instances available"
                            }
                            break
                            
                        case 'Start Server':
                            if (env.HAS_STOPPED_INSTANCES != 'true') {
                                error "Cannot start server: No stopped instances available"
                            }
                            break
                            
                        case 'Display Addresses':
                            if (env.HAS_RUNNING_INSTANCES != 'true') {
                                error "Cannot display addresses: No running instances available"
                            }
                            break
                            
                        case 'Destroy Infrastructure':
                            if (env.HAS_INSTANCES != 'true') {
                                error "Cannot destroy infrastructure: No instances available"
                            }
                            break
                            
                        default:
                            error "Unknown action: ${params.ACTION}"
                    }
                }
            }
        }
        
        stage('Execute Action') {
            steps {
                script {
                    echo "Executing action: ${params.ACTION}"
                    
                    switch(params.ACTION) {
                        case 'Infrastructure Configuration':
                            sh '''
                                set +x
                                echo "=== Creating Infrastructure ==="
                                # Your infrastructure creation script (Terraform, CloudFormation, etc.)
                                # Example for AWS using CloudFormation:
                                # aws cloudformation create-stack --stack-name my-stack --template-body file://template.yaml
                                echo "=== Infrastructure Created ==="
                            '''
                            break
                            
                        case 'Application Deployment':
                            sh '''
                                set +x
                                echo "=== Deploying Application ==="
                                # Your application deployment script
                                # Example: SSH into instances and deploy
                                for INSTANCE_ID in ${INSTANCE_IDS//,/ }; do
                                    IP=$(aws ec2 describe-instances --instance-ids $INSTANCE_ID --query 'Reservations[].Instances[].PublicIpAddress' --output text)
                                    echo "Deploying to $IP..."
                                    # ssh -i key.pem ec2-user@$IP "cd /app && git pull && docker-compose up -d"
                                done
                                echo "=== Application Deployed ==="
                            '''
                            break
                            
                        case 'Stop running Server':
                            sh '''
                                set +x
                                echo "=== Stopping Servers ==="
                                # Get running instance IDs and stop them
                                aws ec2 stop-instances --instance-ids ${INSTANCE_IDS//,/ }
                                echo "=== Servers Stopped ==="
                            '''
                            break
                            
                        case 'Start Server':
                            sh '''
                                set +x
                                echo "=== Starting Servers ==="
                                # Get stopped instance IDs and start them
                                aws ec2 start-instances --instance-ids ${INSTANCE_IDS//,/ }
                                echo "=== Servers Started ==="
                            '''
                            break
                            
                        case 'Display Addresses':
                            sh '''
                                set +x
                                echo "=== Server Addresses ==="
                                # Display public and private IPs
                                aws ec2 describe-instances --instance-ids ${INSTANCE_IDS//,/ } --query 'Reservations[].Instances[].[InstanceId,PublicIpAddress,PrivateIpAddress,Tags[?Key==`Name`].Value | [0]]' --output table
                                echo "==================="
                            '''
                            break
                            
                        case 'Destroy Infrastructure':
                            sh '''
                                set +x
                                echo "=== Destroying Infrastructure ==="
                                # Your infrastructure destruction script
                                # For AWS CloudFormation example:
                                # aws cloudformation delete-stack --stack-name my-stack
                                # Or directly terminate instances:
                                aws ec2 terminate-instances --instance-ids ${INSTANCE_IDS//,/ }
                                echo "=== Infrastructure Destroyed ==="
                            '''
                            break
                    }
                }
            }
        }
    }
    
    post {
        success {
            echo "Pipeline executed successfully"
        }
        failure {
            echo "Pipeline failed"
        }
        always {
            cleanWs()
        }
    }
}