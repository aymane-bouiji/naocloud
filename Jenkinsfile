pipeline {
    agent any
    
    triggers {
        pollSCM('*/5 * * * *')
    }
    
    // Instead of static parameters, we'll use a script to determine parameters
    // based on infrastructure state
    options {
        // This allows parameters to be provided dynamically
        parameters([
            string(name: 'AWS_REGION', defaultValue: 'eu-west-1', description: 'AWS region to use (e.g., eu-west-1)'),
            string(name: 'LOG_LEVEL', defaultValue: 'INFO', description: 'Log detail level: INFO or DEBUG. Defaults to INFO.'),
            string(name: 'CLUSTER_VERSION', defaultValue: '23.09', description: 'Cluster version for naocloud image')
        ])
    }
    
    stages {
        // First stage to determine infrastructure state and set properties
        stage('Detect Infrastructure State') {
            steps {
                script {
                    // Set default AWS region for commands
                    env.AWS_DEFAULT_REGION = params.AWS_REGION
                    
                    // Check for existing infrastructure by looking for terraform state
                    def terraformStateExists = fileExists('/workspace/aws/.terraform')
                    
                    // Determine if instances exist and their state
                    def instancesExist = false
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
                                --output text
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
                                --output text
                            ''',
                            returnStdout: true
                        ).trim()
                        
                        // Set state flags
                        instancesRunning = runningInstances != '0' && runningInstances != ''
                        instancesStopped = stoppedInstances != '0' && stoppedInstances != ''
                        instancesExist = instancesRunning || instancesStopped
                        
                        echo "Infrastructure state detected:"
                        echo "- Terraform state exists: ${terraformStateExists}"
                        echo "- Instances exist: ${instancesExist}"
                        echo "- Running instances: ${instancesRunning}"
                        echo "- Stopped instances: ${instancesStopped}"
                    } catch (Exception e) {
                        echo "Error detecting infrastructure state: ${e.message}"
                        // If we can't determine state, assume nothing exists
                        instancesExist = false
                        instancesRunning = false
                        instancesStopped = false
                    }
                    
                    // Set parameters based on the current state
                    def parameters = []
                    
                    // Always add the basic parameters
                    parameters.add(string(name: 'AWS_REGION', defaultValue: params.AWS_REGION ?: 'eu-west-1', 
                                        description: 'AWS region to use (e.g., eu-west-1)'))
                    parameters.add(string(name: 'LOG_LEVEL', defaultValue: params.LOG_LEVEL ?: 'INFO', 
                                        description: 'Log detail level: INFO or DEBUG. Defaults to INFO.'))
                    parameters.add(string(name: 'CLUSTER_VERSION', defaultValue: params.CLUSTER_VERSION ?: '23.09', 
                                        description: 'Cluster version for naocloud image'))
                    
                    // Add conditional parameters based on state
                    if (!instancesExist) {
                        // If no infrastructure exists, only allow bootstrapping
                        parameters.add(booleanParam(name: 'Infrastructure_Bootstrapping', defaultValue: false, 
                                                 description: 'Set up and create the cloud environment'))
                    } else {
                        // If infrastructure exists, allow configuration and deployment
                        parameters.add(booleanParam(name: 'Infrastructure_Configuration', defaultValue: false, 
                                                 description: 'Configure the cloud setup and manage Application images'))
                        parameters.add(booleanParam(name: 'Application_Deployment', defaultValue: false, 
                                                 description: 'Deploy applications to the cloud'))
                        
                        // Allow destruction of existing infrastructure
                        parameters.add(booleanParam(name: 'Destroy_Infrastructure', defaultValue: false, 
                                                 description: 'Delete the entire cloud environment'))
                        parameters.add(string(name: 'DESTROY_CONFIRMATION', defaultValue: '', 
                                           description: 'Type "destroy" to confirm deletion of the cloud environment'))
                        
                        // Display addresses only if instances are running
                        if (instancesRunning) {
                            parameters.add(booleanParam(name: 'Display_Addresses', defaultValue: false, 
                                                     description: 'Display addresses of running server'))
                            parameters.add(booleanParam(name: 'Stop_running_Server', defaultValue: false, 
                                                     description: 'Pause (stop) the server'))
                        }
                        
                        // Allow starting servers only if stopped instances exist
                        if (instancesStopped) {
                            parameters.add(booleanParam(name: 'Start_Server', defaultValue: false, 
                                                     description: 'Start the stopped server'))
                        }
                    }
                    
                    // Update the build with the new parameters
                    properties([
                        parameters(parameters)
                    ])
                    
                    // If this is a parameterized build, proceed; otherwise, prompt for parameters
                    if (currentBuild.getBuildCauses('hudson.model.Cause$UserIdCause')) {
                        echo "Build triggered by user, checking parameters..."
                    } else {
                        echo "Build triggered automatically, prompting for parameters..."
                        // This will halt the pipeline and prompt for parameters
                        currentBuild.result = 'ABORTED'
                        error("Build requires parameters, please provide them.")
                    }
                }
            }
        }
        
        stage('Parameter Validation') {
            steps {
                script {
                    echo "Validating parameters..."
                    
                    // Contradictory actions
                    if (params.containsKey('Start_Server') && params.containsKey('Stop_running_Server') && 
                        params['Start_Server'] && params['Stop_running_Server']) {
                        error("❌ You cannot start and stop the server at the same time. Please select only one.")
                    }

                    if (params.containsKey('Infrastructure_Bootstrapping') && params.containsKey('Destroy_Infrastructure') && 
                        params['Infrastructure_Bootstrapping'] && params['Destroy_Infrastructure']) {
                        error("❌ You cannot bootstrap and destroy infrastructure at the same time. Please select only one.")
                    }

                    if (params.containsKey('Destroy_Infrastructure') && params['Destroy_Infrastructure']) {
                        // Check other conflicting actions
                        def conflictingActions = []
                        if (params.containsKey('Infrastructure_Configuration') && params['Infrastructure_Configuration']) 
                            conflictingActions.add('Infrastructure Configuration')
                        if (params.containsKey('Application_Deployment') && params['Application_Deployment']) 
                            conflictingActions.add('Application Deployment')
                        if (params.containsKey('Start_Server') && params['Start_Server']) 
                            conflictingActions.add('Start Server')
                        if (params.containsKey('Stop_running_Server') && params['Stop_running_Server']) 
                            conflictingActions.add('Stop running Server')
                        if (params.containsKey('Display_Addresses') && params['Display_Addresses']) 
                            conflictingActions.add('Display Addresses')
                        
                        if (conflictingActions.size() > 0) {
                            error("❌ Cannot perform other actions while destroying infrastructure. Conflicting actions: ${conflictingActions.join(', ')}")
                        }

                        // Verify destruction confirmation
                        if (!params.containsKey('DESTROY_CONFIRMATION') || params.DESTROY_CONFIRMATION != 'destroy') {
                            error("❌ Destroy confirmation not provided. Please type 'destroy' in DESTROY_CONFIRMATION to proceed.")
                        }
                    }

                    // Check if at least one action is selected
                    def anyActionSelected = false
                    ['Infrastructure_Bootstrapping', 'Infrastructure_Configuration', 'Application_Deployment', 
                     'Destroy_Infrastructure', 'Start_Server', 'Stop_running_Server', 'Display_Addresses'].each { action ->
                        if (params.containsKey(action) && params[action]) {
                            anyActionSelected = true
                        }
                    }
                    
                    if (!anyActionSelected) {
                        error("⚠️ No action selected. Please choose at least one operation.")
                    }
                }
            }
        }

        stage('Infrastructure Bootstrapping') {
            when {
                expression { params.containsKey('Infrastructure_Bootstrapping') && params['Infrastructure_Bootstrapping'] }
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
                expression { params.containsKey('Infrastructure_Configuration') && params['Infrastructure_Configuration'] }
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
                expression { params.containsKey('Application_Deployment') && params['Application_Deployment'] }
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
                expression { params.containsKey('Stop_running_Server') && params['Stop_running_Server'] }
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
                expression { params.containsKey('Start_Server') && params['Start_Server'] }
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
                allOf {
                    expression { params.containsKey('Destroy_Infrastructure') && params['Destroy_Infrastructure'] }
                    expression { params.containsKey('DESTROY_CONFIRMATION') && params.DESTROY_CONFIRMATION == 'destroy' }
                }
            }
            steps {
                dir("/workspace/aws") {
                    sh 'terraform destroy -auto-approve'
                }
            }
        }
        
        stage('Display addresses of the server') {
            when {
                expression { params.containsKey('Display_Addresses') && params['Display_Addresses'] }
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
        always {
            cleanWs()
        }
    }
}