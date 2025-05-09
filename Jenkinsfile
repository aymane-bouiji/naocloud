pipeline {
    agent any
    
    triggers {
        pollSCM('*/5 * * * *')
    }
    
    // Define base parameters
    parameters {
        string(name: 'AWS_REGION', defaultValue: 'eu-west-1', description: 'AWS region to use (e.g., eu-west-1)')
        string(name: 'LOG_LEVEL', defaultValue: 'INFO', description: 'Log detail level: INFO or DEBUG. Defaults to INFO.')
        string(name: 'CLUSTER_VERSION', defaultValue: '23.09', description: 'Cluster version for naocloud image')
        // Action parameter with default options - will be used if state file doesn't exist
        choice(name: 'ACTION', choices: ['Infrastructure Bootstrapping'], description: 'Select an action to perform')
    }
    
    stages {
        stage('Active Parameter Detection') {
            steps {
                script {
                    // Set AWS region for commands
                    env.AWS_DEFAULT_REGION = params.AWS_REGION
                    
                    echo "Detecting infrastructure state..."
                    
                    // Check for existing infrastructure by looking for terraform state
                    def terraformStateExists = false
                    try {
                        dir("/workspace/aws") {
                            terraformStateExists = sh(
                                script: 'test -d .terraform && echo "true" || echo "false"', 
                                returnStdout: true
                            ).trim() == "true" || fileExists('/workspace/aws/terraform.tfstate')
                        }
                    } catch (Exception e) {
                        echo "Error checking terraform state: ${e.message}"
                    }
                    
                    // Determine if instances exist and their state
                    def instancesRunning = false
                    def instancesStopped = false
                    
                    try {
                        // Check for running instances
                        def runningInstances = sh(
                            script: '''
                                aws ec2 describe-instances \
                                --filters "Name=tag:Name,Values=master_instance,worker_instance" \
                                "Name=instance-state-name,Values=running,pending" \
                                --query "length(Reservations[].Instances[])" \
                                --output text || echo "0"
                            ''',
                            returnStdout: true
                        ).trim()
                        
                        // Check for stopped instances
                        def stoppedInstances = sh(
                            script: '''
                                aws ec2 describe-instances \
                                --filters "Name=tag:Name,Values=master_instance,worker_instance" \
                                "Name=instance-state-name,Values=stopped" \
                                --query "length(Reservations[].Instances[])" \
                                --output text || echo "0"
                            ''',
                            returnStdout: true
                        ).trim()
                        
                        // Set state flags
                        instancesRunning = runningInstances != '0' && runningInstances != ''
                        instancesStopped = stoppedInstances != '0' && stoppedInstances != ''
                        
                        echo "Infrastructure state detected:"
                        echo "- Terraform state exists: ${terraformStateExists}"
                        echo "- Running instances: ${instancesRunning}"
                        echo "- Stopped instances: ${instancesStopped}"
                    } catch (Exception e) {
                        echo "Error detecting instance state: ${e.message}"
                        // If we can't determine state, assume nothing exists
                        instancesRunning = false
                        instancesStopped = false
                    }
                    
                    // Generate the action choices based on current state
                    def actionChoices = []
                    
                    // Always available
                    if (!terraformStateExists) {
                        actionChoices.add("Infrastructure Bootstrapping")
                    } else {
                        // If infrastructure exists
                        actionChoices.add("Infrastructure Configuration")
                        actionChoices.add("Application Deployment")
                        actionChoices.add("Destroy Infrastructure")
                        
                        // Instance specific actions
                        if (instancesRunning) {
                            actionChoices.add("Stop running Server")
                            actionChoices.add("Display Addresses")
                        }
                        
                        if (instancesStopped) {
                            actionChoices.add("Start Server")
                        }
                    }
                    
                    // Store the current state in a state file that persists between builds
                    // The state file will remain in the workspace
                    def stateFile = "${WORKSPACE}/infrastructure_state.txt"
                    def stateContent = """TERRAFORM_STATE_EXISTS=${terraformStateExists}
INSTANCES_RUNNING=${instancesRunning}
INSTANCES_STOPPED=${instancesStopped}
AVAILABLE_ACTIONS=${actionChoices.join(',')}
"""
                    writeFile file: stateFile, text: stateContent
                    
                    // Display available actions to the user
                    echo "Available actions based on current infrastructure state:"
                    actionChoices.each { action ->
                        echo "- ${action}"
                    }
                    
                    // If the selected ACTION is not in the available choices, prompt the user
                    if (!actionChoices.contains(params.ACTION)) {
                        echo "Current selected action '${params.ACTION}' is not valid in the current state."
                        def result = input(
                            id: 'userInput',
                            message: 'Select an action to perform',
                            parameters: [
                                choice(name: 'ACTION', choices: actionChoices, 
                                       description: 'Select an action to perform on the cloud infrastructure')
                            ]
                        )
                        env.ACTION = result.ACTION
                    } else {
                        env.ACTION = params.ACTION
                    }
                    
                    echo "Proceeding with action: ${env.ACTION}"
                    
                    // Write the action choices to a file that can be read by the Jenkins job configuration
                    // This file can be used by a simple script in Jenkins job configuration to update parameters
                    writeFile file: "${WORKSPACE}/action_choices.txt", text: actionChoices.join('\n')
                }
            }
        }
        
        stage('Parameter Validation') {
            when {
                expression { env.ACTION == "Destroy Infrastructure" }
            }
            steps {
                script {
                    // Validate destroy confirmation for destructive action
                    def destroyConfirm = input(
                        id: 'destroyConfirm',
                        message: 'Type "destroy" to confirm deletion of the cloud environment',
                        parameters: [
                            string(name: 'DESTROY_CONFIRMATION', defaultValue: '', 
                                   description: 'Type "destroy" to confirm deletion of the cloud environment')
                        ]
                    )
                    
                    if (destroyConfirm.DESTROY_CONFIRMATION != 'destroy') {
                        error("❌ Destroy confirmation not provided. You must type 'destroy' to proceed with infrastructure deletion.")
                    }
                }
            }
        }

        stage('Infrastructure Bootstrapping') {
            when {
                expression { env.ACTION == "Infrastructure Bootstrapping" }
            }
            steps {
                dir("/workspace/aws") {
                    sh 'terraform init'
                    sh 'terraform plan -out=tfplan'
                    sh 'terraform apply -auto-approve tfplan'
                }
            }
        }
        
        stage('Infrastructure Configuration') {
            when {
                expression { env.ACTION == "Infrastructure Configuration" }
            }
            steps {
                dir("/workspace/ansible") {
                    sh "AWS_REGION=${params.AWS_REGION} ansible-inventory -i aws_ec2.yaml --list"

                    sh """
                        ansible-playbook -i aws_ec2.yaml aws_playbook.yaml \
                            --private-key=/workspace/aws/id_rsa \
                            -e \"ansible_ssh_common_args='-o StrictHostKeyChecking=no'\" 
                    """
                    
                    sh """
                        ansible-playbook -i aws_ec2.yaml push_load_playbook-1.yaml \
                            --private-key=/workspace/aws/id_rsa \
                            -e \"ansible_ssh_common_args='-o StrictHostKeyChecking=no'\" \
                            -e \"naocloud_tag=${params.CLUSTER_VERSION}\" \
                            -e \"naogizmo_tag=${params.CLUSTER_VERSION}\"
                    """
                }
            }
        }
        
        stage('Application Deployment') {
            when {
                expression { env.ACTION == "Application Deployment" }
            }
            steps {
                dir("/workspace/ansible") {
                    sh """
                        ansible-playbook -i aws_ec2.yaml helm-playbook.yaml  \
                            --private-key=/workspace/aws/id_rsa \
                            -e \"ansible_ssh_common_args='-o StrictHostKeyChecking=no'\" 
                    """
                }
            }
        }
        
        stage('Stop Server') {
            when {
                expression { env.ACTION == "Stop running Server" }
            }
            steps {
                sh '''
                    export AWS_DEFAULT_REGION=${AWS_REGION}
                    MASTER_IDS=$(aws ec2 describe-instances \
                        --filters "Name=tag:Name,Values=master_instance" "Name=instance-state-name,Values=running,pending" \
                        --query "Reservations[].Instances[].InstanceId" \
                        --output text)
                    WORKER_IDS=$(aws ec2 describe-instances \
                        --filters "Name=tag:Name,Values=worker_instance" "Name=instance-state-name,Values=running,pending" \
                        --query "Reservations[].Instances[].InstanceId" \
                        --output text)
                    INSTANCE_IDS="$MASTER_IDS $WORKER_IDS"
                    if [ -n "$INSTANCE_IDS" ]; then
                        echo "Stopping instances: $INSTANCE_IDS"
                        aws ec2 stop-instances --instance-ids $INSTANCE_IDS
                    else
                        echo "No running instances found with tags Name=master_instance or Name=worker_instance"
                    fi
                '''
            }
        }
        
        stage('Start Server') {
            when {
                expression { env.ACTION == "Start Server" }
            }
            steps {
                sh '''
                    export AWS_DEFAULT_REGION=${AWS_REGION}
                    MASTER_IDS=$(aws ec2 describe-instances \
                        --filters "Name=tag:Name,Values=master_instance" "Name=instance-state-name,Values=stopped" \
                        --query "Reservations[].Instances[].InstanceId" \
                        --output text)
                    WORKER_IDS=$(aws ec2 describe-instances \
                        --filters "Name=tag:Name,Values=worker_instance" "Name=instance-state-name,Values=stopped" \
                        --query "Reservations[].Instances[].InstanceId" \
                        --output text)
                    INSTANCE_IDS="$MASTER_IDS $WORKER_IDS"
                    if [ -n "$INSTANCE_IDS" ]; then
                        echo "Starting instances: $INSTANCE_IDS"
                        aws ec2 start-instances --instance-ids $INSTANCE_IDS
                    else
                        echo "No stopped instances found with tags Name=master_instance or Name=worker_instance"
                    fi
                '''
            }
        }
        
        stage('Destroy Infrastructure') {
            when {
                expression { env.ACTION == "Destroy Infrastructure" }
            }
            steps {
                dir("/workspace/aws") {
                    sh 'terraform destroy -auto-approve'
                }
            }
        }
        
        stage('Display addresses of the server') {
            when {
                expression { env.ACTION == "Display Addresses" }
            }
            steps {
                sh '''
                    set +x
                    export AWS_DEFAULT_REGION=${AWS_REGION}
                    echo "=== Worker Instance Public Addresses ==="
                    WORKER_DATA=$(aws ec2 describe-instances \
                        --filters "Name=tag:Name,Values=worker_instance" "Name=instance-state-name,Values=running,pending" \
                        --query "Reservations[].Instances[].[PublicIpAddress,PublicDnsName]" \
                        --output text)
                    if [ -n "$WORKER_DATA" ]; then
                        echo "$WORKER_DATA" | while read -r ip dns; do
                            echo "Worker $((i+=1)):"
                            echo "  Public IP: $ip"
                            echo "  Public DNS: $dns"
                        done
                    else
                        echo "No running worker instances found with tag Name=worker_instance"
                    fi
                    echo "======================================"
                '''
            }
        }
    }
    
    post {
        success {
            echo "Pipeline completed successfully with action: ${env.ACTION}"
        }
        failure {
            echo "Pipeline failed during execution of action: ${env.ACTION}"
        }
        always {
            cleanWs()
        }
    }
}