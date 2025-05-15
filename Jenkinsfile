pipeline {
    agent any
    
    environment {
        AWS_DEFAULT_REGION = 'eu-west-1' // Set globally for all stages
    }
    
    triggers {
        pollSCM('*/5 * * * *')
    }
    
    parameters {
        choice(
            name: 'ACTION',
            choices: ['Detect Infrastructure State'],
            description: '''
                Select an action to perform on the cloud infrastructure. Available actions:

                - Detect Infrastructure State: Checks the current state of NaoServer.
            '''
        )
    }
    
    stages {
        stage('Validate Parameters') {
            when {
                expression { params.ACTION != 'Detect Infrastructure State' }
            }
            steps {
                script {
                    echo "Validating parameters for action: ${params.ACTION}"
                    if (params.ACTION == "Destroy Infrastructure" && 
                        (!params.containsKey('DESTROY_CONFIRMATION') || params.DESTROY_CONFIRMATION != 'destroy')) {
                        error("❌ Destroy confirmation not provided. Please type 'destroy' in DESTROY_CONFIRMATION to proceed.")
                    }
                    if (params.ACTION == "No actions available") {
                        error("⚠️ No valid actions selected. Please run detection to update available actions.")
                    }
                }
            }
        }
        
        stage('Detect Infrastructure State') {
            when {
                expression { params.ACTION == 'Detect Infrastructure State' }
            }
            steps {
                script {
                    echo "Detecting infrastructure state..."
                    
                    // Check for actual AWS infrastructure instead of just terraform state files
                    def terraformStateExists = false
                    try {
                        dir("/workspace/aws") {
                            // Check if there are any instances with our tags in AWS
                            def instanceCount = sh(
                                script: '''
                                    aws ec2 describe-instances \
                                    --filters "Name=tag:Name,Values=master_instance,worker_instance" \
                                    --query "length(Reservations[].Instances[])" \
                                    --output text || echo "0"
                                ''',
                                returnStdout: true
                            ).trim()
                            
                            // Also check terraform state but look for resources, not just file existence
                            def tfStateEmpty = false
                            if (fileExists('/workspace/aws/terraform.tfstate')) {
                                tfStateEmpty = sh(
                                    script: 'grep -q \'"resources": \\[\\]\' terraform.tfstate && echo "true" || echo "false"',
                                    returnStdout: true
                                ).trim() == "true"
                            }
                            
                            // True if there are instances or if state file exists with resources
                            terraformStateExists = (instanceCount != '0' && instanceCount != '') || 
                                                 (fileExists('/workspace/aws/terraform.tfstate') && !tfStateEmpty)
                            
                            echo "Terraform active state exists: ${terraformStateExists}"
                            echo "Instance count: ${instanceCount}"
                        }
                    } catch (Exception e) {
                        echo "Error checking terraform state: ${e.message}"
                    }
                    
                    // Determine instance states
                    def instancesRunning = false
                    def instancesStopped = false
                    try {
                        // Debug: Log all instances with relevant tags
                        def instanceDetails = sh(
                            script: '''
                                aws ec2 describe-instances \
                                --filters "Name=tag:Name,Values=master_instance,worker_instance" \
                                --query "Reservations[].Instances[].[InstanceId,State.Name]" \
                                --output text
                            ''',
                            returnStdout: true
                        ).trim()
                        echo "Instance details: ${instanceDetails}"
                        
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
                        
                        instancesRunning = runningInstances != '0' && runningInstances != ''
                        instancesStopped = stoppedInstances != '0' && stoppedInstances != ''
                        
                        echo "Infrastructure state detected:"
                        echo "- Running instances count: ${runningInstances} (instancesRunning: ${instancesRunning})"
                        echo "- Stopped instances count: ${stoppedInstances} (instancesStopped: ${instancesStopped})"
                    } catch (Exception e) {
                        echo "Error detecting instance state: ${e.message}"
                        instancesRunning = false
                        instancesStopped = false
                    }
                    
                    // Dynamic action choices
                    def actionChoices = []
                    def actionDescriptions = [:]
                    if (!terraformStateExists) {
                        actionChoices.add("Infrastructure Bootstrapping")
                        actionDescriptions["Infrastructure Bootstrapping"] = "Creates the infrastructure for NaoServer."
                    } else {
                        actionChoices.add("Cluster Bootstrapping")
                        actionChoices.add("Application Deployment")
                        actionChoices.add("Destroy Infrastructure")
                        actionDescriptions["Cluster Bootstrapping"] = "Configure the cluster hosting NaoServer."
                        actionDescriptions["Application Deployment"] = "Install Naoserver applications in the cluster."
                        actionDescriptions["Destroy Infrastructure"] = "Destroys all NaoServer infrastructure (requires destroy confirmation)."
                        // Always include Display Addresses to allow checking even if no instances
                        actionChoices.add("Display Addresses")
                        actionDescriptions["Display Addresses"] = "Displays IP addresses of NaoServer instances."
                        if (instancesRunning) {
                            actionChoices.add("Stop running Server")
                            actionDescriptions["Stop running Server"] = "Stops NaoServer running instances."
                        }
                        if (instancesStopped) {
                            actionChoices.add("Start Server")
                            actionDescriptions["Start Server"] = "Starts stopped EC2 instances tagged as master_instance or worker_instance."
                        }
                    }
                    
                    // Ensure at least one action
                    if (actionChoices.isEmpty()) {
                        actionChoices.add("No actions available")
                        actionDescriptions["No actions available"] = "No valid actions are available based on the current infrastructure state."
                    }
                    
                    // Debug: Log action choices
                    echo "Action choices: ${actionChoices.join(', ')}"
                    
                    // Build description for ACTION parameter with spacing
                    def actionDescription = "Select an action to perform on the cloud infrastructure. Available actions:\n\n"
                    actionChoices.each { choice ->
                        actionDescription += "- ${choice}: ${actionDescriptions[choice]}\n\n"
                    }
                    
                    // Additional parameters
                    def dynamicParams = []
                    dynamicParams.add(choice(
                        name: 'ACTION',
                        choices: actionChoices,
                        description: actionDescription
                    ))
                    
                    // Only add NaoCloud_Version parameter for specific actions that need it
                    if (actionChoices.contains("Application Deployment") || 
                        actionChoices.contains("Cluster Bootstrapping") || 
                        actionChoices.contains("Infrastructure Bootstrapping")) {
                        
                        // Get current value if exists, otherwise use default
                        def currentVersion = params.containsKey('NaoCloud_Version') ? params.NaoCloud_Version : '23.09'
                        
                        dynamicParams.add(string(
                            name: 'NaoCloud_Version',
                            defaultValue: currentVersion, 
                            description: 'NaoCloud version: please enter the version of naocloud release you want to install'
                        ))
                    }
                    
                    // Only add DESTROY_CONFIRMATION for Destroy Infrastructure action
                    if (actionChoices.contains("Destroy Infrastructure")) {
                        dynamicParams.add(string(
                            name: 'DESTROY_CONFIRMATION',
                            defaultValue: '',
                            description: 'Type "destroy" to confirm deletion of the cloud environment'
                        ))
                    }
                    
                    // Update pipeline parameters
                    properties([
                        parameters(dynamicParams)
                    ])
                    
                    // Exit gracefully
                    currentBuild.description = "Infrastructure detection complete. Valid actions: ${actionChoices.join(', ')}. Please run the build again with your selected action."
                    echo "Detection complete. Please rerun the build to select an action."
                    return // Exit pipeline without error
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
        
        stage('Cluster Bootstrapping') {
            when {
                expression { params.ACTION == "Cluster Bootstrapping" }
            }
            steps {
                dir("/workspace/ansible") {
                    sh "ansible-inventory -i aws_ec2.yaml --list"
                    sh """
                        ansible-playbook -i aws_ec2.yaml configure_cluster_playbook.yaml \
                            --private-key=/workspace/aws/id_rsa \
                            -e \"ansible_ssh_common_args='-o StrictHostKeyChecking=no'\" 
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
                        ansible-playbook -i aws_ec2.yaml configure_images_playbook.yaml \
                            --private-key=/workspace/aws/id_rsa \
                            -e \"ansible_ssh_common_args='-o StrictHostKeyChecking=no'\" \
                            -e \"naocloud_tag=${params.NaoCloud_Version}\" \
                            -e \"naogizmo_tag=${params.NaoCloud_Version}\"
                    """



                    sh """
                        ansible-playbook -i aws_ec2.yaml helm-playbook.yaml \
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
                script {
                    try {
                        sh '''
                            export AWS_DEFAULT_REGION=${AWS_DEFAULT_REGION}
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
                    } catch (Exception e) {
                        error("Failed to stop servers: ${e.message}. Ensure AWS credentials and region (${AWS_DEFAULT_REGION}) are valid.")
                    }
                }
            }
        }
        
        stage('Start Server') {
            when {
                expression { params.ACTION == "Start Server" }
            }
            steps {
                script {
                    try {
                        sh '''
                            export AWS_DEFAULT_REGION=${AWS_DEFAULT_REGION}
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
                    } catch (Exception e) {
                        error("Failed to start servers: ${e.message}. Ensure AWS credentials and region (${AWS_DEFAULT_REGION}) are valid.")
                    }
                }
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
        
        stage('Display Addresses') {
            when {
                expression { params.ACTION == "Display Addresses" }
            }
            steps {
                script {
                    try {
                        sh '''
                            set +x
                            export AWS_DEFAULT_REGION=${AWS_DEFAULT_REGION}
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
                    } catch (Exception e) {
                        error("Failed to display addresses: ${e.message}. Ensure AWS credentials and region (${AWS_DEFAULT_REGION}) are valid.")
                    }
                }
            }
        }
    }
    
    post {
        always {
            cleanWs()
        }
    }
}