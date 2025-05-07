pipeline {
    agent any
    
    triggers {
        pollSCM('*/5 * * * *')
    }
    
    // Use properties block for dynamic parameters
    options {
        // Disable concurrent builds to avoid race conditions with parameter updates
        disableConcurrentBuilds()
    }
    
    stages {
        stage('Setup Parameters') {
            steps {
                script {
                    properties([
                        parameters([
                            string(name: 'AWS_REGION', defaultValue: 'eu-west-1', description: 'AWS region to use (e.g., eu-west-1)'),
                            string(name: 'LOG_LEVEL', defaultValue: 'INFO', description: 'Log detail level: INFO or DEBUG. Defaults to INFO.'),
                            string(name: 'CLUSTER_VERSION', defaultValue: '23.09', description: 'Cluster version for naocloud image'),
                            // This is a special parameter that triggers the autodetection
                            string(name: '_DETECT_INFRA', defaultValue: "${currentBuild.number}", description: 'Internal parameter for infrastructure detection')
                        ])
                    ])
                    
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
                    
                    // Dynamic ACTION choices based on infrastructure state
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
                    
                    // Additional parameters based on selected actions
                    def dynamicParams = []
                    
                    // Always include the base parameters
                    dynamicParams.add(string(name: 'AWS_REGION', defaultValue: params.AWS_REGION, 
                                           description: 'AWS region to use (e.g., eu-west-1)'))
                    dynamicParams.add(string(name: 'LOG_LEVEL', defaultValue: params.LOG_LEVEL, 
                                          description: 'Log detail level: INFO or DEBUG. Defaults to INFO.'))
                    dynamicParams.add(string(name: 'CLUSTER_VERSION', defaultValue: params.CLUSTER_VERSION, 
                                          description: 'Cluster version for naocloud image'))
                    
                    // Add dynamic ACTION parameter
                    dynamicParams.add(choice(name: 'ACTION', choices: actionChoices, 
                                          description: 'Select an action to perform on the cloud infrastructure'))
                    
                    // Add confirmation for destroy
                    if (actionChoices.contains("Destroy Infrastructure")) {
                        dynamicParams.add(string(name: 'DESTROY_CONFIRMATION', defaultValue: '', 
                                              description: 'Type "destroy" to confirm deletion of the cloud environment'))
                    }
                    
                    // Add hidden parameter for infra detection
                    dynamicParams.add(string(name: '_DETECT_INFRA', defaultValue: "${currentBuild.number}", 
                                            description: 'Internal parameter for infrastructure detection'))
                    
                    // Update the build with dynamic parameters
                    properties([
                        parameters(dynamicParams)
                    ])
                    
                    // If this is the first run (parameters being set), abort the build and ask user to run again with parameters
                    if (currentBuild.number == 1 || !params.containsKey('ACTION')) {
                        currentBuild.description = "Infrastructure detection complete. Please run the build with your selected action."
                        currentBuild.result = 'ABORTED'
                        error("Infrastructure state detected. Please select an action from the parameters and run the build again.")
                    }
                }
            }
        }
        
        stage('Parameter Validation') {
            steps {
                script {
                    echo "Validating parameters for action: ${params.ACTION}"
                    
                    // Validate destroy confirmation
                    if (params.ACTION == "Destroy Infrastructure" && 
                        (!params.containsKey('DESTROY_CONFIRMATION') || params.DESTROY_CONFIRMATION != 'destroy')) {
                        error("❌ Destroy confirmation not provided. Please type 'destroy' in DESTROY_CONFIRMATION to proceed.")
                    }
                }
            }
        }

        stage('Infrastructure Bootstrapping') {
            when {
                expression { params.ACTION == "Infrastructure Bootstrapping" }
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
                expression { params.ACTION == "Infrastructure Configuration" }
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
                expression { params.ACTION == "Application Deployment" }
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
                expression { params.ACTION == "Stop running Server" }
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
                expression { params.ACTION == "Start Server" }
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
                    expression { params.ACTION == "Destroy Infrastructure" }
                    expression { params.DESTROY_CONFIRMATION == 'destroy' }
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
                expression { params.ACTION == "Display Addresses" }
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