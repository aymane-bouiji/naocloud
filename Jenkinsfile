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
            choices: ['Detect Infrastructure State', 'Deploy NaoServer', 'Start NaoServer', 'Stop running NaoServer', 'Restart NaoServer', 'Display Addresses', 'Destroy Infrastructure'],
            description: '''
                Select an action to perform on the cloud infrastructure. Available actions:

                - Detect Infrastructure State: Checks the current state of NaoServer.
                - Deploy NaoServer: Creates infrastructure, configures cluster, and deploys applications.
                - Start NaoServer: Starts the stopped NaoServer.
                - Stop running NaoServer: Stops NaoServer running instances.
                - Restart NaoServer: Reboots all running NaoServer instances.
                - Display Addresses: Displays IP addresses of NaoServer instances.
                - Destroy Infrastructure: Destroys all NaoServer infrastructure (requires destroy confirmation).
            '''
        )
        string(
            name: 'naocloud_version',
            defaultValue: '23.09',
            description: 'NaoCloud version: please enter the version of naocloud release you want to install'
        )
        string(
            name: 'destroy_confirmation',
            defaultValue: '',
            description: 'Type "destroy" to confirm deletion of the cloud environment (only needed for Destroy Infrastructure action)'
        )
    }
    
    stages {
        stage('Deploy NaoServer') {
            when {
                expression { return params.ACTION == "Deploy NaoServer" }
            }
            steps {
                script {
                    echo "Running NaoServer Deployment (includes Infrastructure, Cluster, and Application)..."
                    
                    // Step 1: Infrastructure Bootstrapping
                    dir("/workspace/aws") {
                        echo "Step 1/3: Infrastructure Bootstrapping..."
                        sh 'terraform init'
                        sh 'terraform plan -out=tfplan'
                        sh 'terraform apply -auto-approve tfplan'
                    }
                    
                    // Step 2: Cluster Bootstrapping
                    dir("/workspace/ansible") {
                        echo "Step 2/3: Cluster Bootstrapping..."
                        sh "chmod 600 /workspace/aws/id_rsa "
                        sh "ansible-inventory -i aws_ec2.yaml --list"
                        sh """
                            ansible-playbook -i aws_ec2.yaml configure_cluster_playbook.yaml \
                                --private-key=/workspace/aws/id_rsa \
                                -e \"ansible_ssh_common_args='-o StrictHostKeyChecking=no'\" 
                        """
                    }
                    
                    // Step 3: Application Deployment
                    dir("/workspace/ansible") {
                        echo "Step 3/3: Application Deployment..."
                        sh """
                            ansible-playbook -i aws_ec2.yaml configure_images_playbook.yaml \
                                --private-key=/workspace/aws/id_rsa \
                                -e \"ansible_ssh_common_args='-o StrictHostKeyChecking=no'\" \
                                -e \"naocloud_tag=${params.naocloud_version}\" \
                                -e \"naogizmo_tag=${params.naocloud_version}\"
                        """
                        sh """
                            ansible-playbook -i aws_ec2.yaml helm-playbook.yaml \
                                --private-key=/workspace/aws/id_rsa \
                                -e \"ansible_ssh_common_args='-o StrictHostKeyChecking=no'\" 
                        """
                    }
                    
                    echo "NaoServer deployment complete!"
                }
            }
        }
        
        stage('Stop NaoServer') {
            when {
                expression { return params.ACTION == "Stop running NaoServer" }
            }
            steps {
                script {
                    try {
                        // First check if there are any running instances
                        def runningInstancesCheck = sh(
                            script: '''
                                export AWS_DEFAULT_REGION=${AWS_DEFAULT_REGION}
                                aws ec2 describe-instances \
                                    --filters "Name=tag:Name,Values=master_instance,worker_instance" "Name=instance-state-name,Values=running,pending" \
                                    --query "length(Reservations[].Instances[])" \
                                    --output text || echo "0"
                            ''',
                            returnStdout: true
                        ).trim()
                        
                        if (runningInstancesCheck == '0' || runningInstancesCheck == '') {
                            echo "WARNING: No running instances found with tags Name=master_instance or Name=worker_instance"
                            echo "This action may not have any effect."
                        }
                    
                        // Stop instances and wait for them to reach stopped state
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
                                echo "Waiting for instances to reach stopped state..."
                                aws ec2 wait instance-stopped --instance-ids $INSTANCE_IDS
                                echo "Instances have reached stopped state."
                            else
                                echo "No running instances found with tags Name=master_instance or Name=worker_instance"
                            fi
                        '''
                    } catch (Exception e) {
                        error("Failed to stop NaoServer: ${e.message}. Ensure AWS credentials and region (${AWS_DEFAULT_REGION}) are valid.")
                    }
                }
            }
        }
        
        stage('Start NaoServer') {
            when {
                expression { return params.ACTION == "Start NaoServer" }
            }
            steps {
                script {
                    try {
                        // First check if there are any stopped instances
                        def stoppedInstancesCheck = sh(
                            script: '''
                                export AWS_DEFAULT_REGION=${AWS_DEFAULT_REGION}
                                aws ec2 describe-instances \
                                    --filters "Name=tag:Name,Values=master_instance,worker_instance" "Name=instance-state-name,Values=stopped" \
                                    --query "length(Reservations[].Instances[])" \
                                    --output text || echo "0"
                            ''',
                            returnStdout: true
                        ).trim()
                        
                        if (stoppedInstancesCheck == '0' || stoppedInstancesCheck == '') {
                            echo "WARNING: No stopped instances found with tags Name=master_instance or Name=worker_instance"
                            echo "This action may not have any effect."
                        }
                    
                        // Start instances and wait for them to reach running state
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
                                echo "Waiting for instances to reach running state..."
                                aws ec2 wait instance-running --instance-ids $INSTANCE_IDS
                                echo "Instances have reached running state."
                            else
                                echo "No stopped instances found with tags Name=master_instance or Name=worker_instance"
                            fi
                        '''
                    } catch (Exception e) {
                        error("Failed to start NaoServer: ${e.message}. Ensure AWS credentials and region (${AWS_DEFAULT_REGION}) are valid.")
                    }
                }
            }
        }

        stage('Restart NaoServer') {
            when {
                expression { return params.ACTION == "Restart NaoServer" }
            }
            steps {
                script {
                    dir("/workspace/ansible") {
                        echo "Restarting NaoServer..."
                        
                        sh """
                            ansible-playbook -i aws_ec2.yaml restart-cluster-playbook.yaml \
                                --private-key=/workspace/aws/id_rsa \
                                -e \"ansible_ssh_common_args='-o StrictHostKeyChecking=no'\" 
                        """
                    }
                    
                    echo "NaoServer Restarted!"
                }
            }
        }
        
        stage('Destroy Infrastructure') {
            when {
                allOf {
                    expression { return params.ACTION == "Destroy Infrastructure" }
                    expression { return params.destroy_confirmation == 'destroy' }
                }
            }
            steps {
                script {
                    try {
                        // First check if there is any infrastructure to destroy
                        def instanceCheck = sh(
                            script: '''
                                export AWS_DEFAULT_REGION=${AWS_DEFAULT_REGION}
                                aws ec2 describe-instances \
                                    --filters "Name=tag:Name,Values=master_instance,worker_instance" \
                                    --query "length(Reservations[].Instances[])" \
                                    --output text || echo "0"
                            ''',
                            returnStdout: true
                        ).trim()
                        
                        if (instanceCheck == '0' || instanceCheck == '') {
                            echo "WARNING: No infrastructure found to destroy. This action may not have any effect."
                        }
                        
                        dir("/workspace/aws") {
                            sh 'terraform destroy -auto-approve'
                        }
                    } catch (Exception e) {
                        error("Failed to destroy infrastructure: ${e.message}")
                    }
                }
            }
        }
        
        stage('Display Addresses') {
            when {
                expression { return params.ACTION == "Display Addresses" }
            }
            steps {
                script {
                    try {
                        // First check if there are any running instances
                        def runningInstancesCheck = sh(
                            script: '''
                                export AWS_DEFAULT_REGION=${AWS_DEFAULT_REGION}
                                aws ec2 describe-instances \
                                    --filters "Name=tag:Name,Values=master_instance,worker_instance" "Name=instance-state-name,Values=running" \
                                    --query "length(Reservations[].Instances[])" \
                                    --output text || echo "0"
                            ''',
                            returnStdout: true
                        ).trim()
                        
                        if (runningInstancesCheck == '0' || runningInstancesCheck == '') {
                            echo "WARNING: No running instances found with tags Name=master_instance or Name=worker_instance"
                            echo "There may be no addresses to display."
                        }
                    
                        sh '''
                            set +x
                            export AWS_DEFAULT_REGION=${AWS_DEFAULT_REGION}
                            echo "=== Master Instance Public Addresses ==="
                            MASTER_DATA=$(aws ec2 describe-instances \
                                --filters "Name=tag:Name,Values=master_instance" "Name=instance-state-name,Values=running" \
                                --query "Reservations[].Instances[].[PublicIpAddress,PublicDnsName]" \
                                --output text)
                            if [ -n "$MASTER_DATA" ]; then
                                i=0
                                echo "$MASTER_DATA" | while read -r ip dns; do
                                    echo "Master $((i+=1)):"
                                    echo "  Public IP: $ip"
                                    echo "  Public DNS: $dns"
                                done
                            else
                                echo "No running master instances found with tag Name=master_instance"
                            fi
                            echo ""
                            
                            echo "=== Worker Instance Public Addresses ==="
                            WORKER_DATA=$(aws ec2 describe-instances \
                                --filters "Name=tag:Name,Values=worker_instance" "Name=instance-state-name,Values=running" \
                                --query "Reservations[].Instances[].[PublicIpAddress,PublicDnsName]" \
                                --output text)
                            if [ -n "$WORKER_DATA" ]; then
                                i=0
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
        
        stage('Detect Infrastructure State') {
            steps {
                script {
                    echo "Detecting infrastructure state..."
                    try {
                        // Check for running instances
                        def runningInstances = sh(
                            script: '''
                            aws ec2 describe-instances \
                                --filters "Name=tag:Name,Values=master_instance,worker_instance" \
                                          "Name=instance-state-name,Values=running" \
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
    
                        // Check for total instances (all states)
                        def totalInstances = sh(
                            script: '''
                            aws ec2 describe-instances \
                                --filters "Name=tag:Name,Values=master_instance,worker_instance" \
                                --query "length(Reservations[].Instances[])" \
                                --output text || echo "0"
                            ''',
                            returnStdout: true
                        ).trim()
    
                        // Check for terminated instances
                        def terminatedInstances = sh(
                            script: '''
                            aws ec2 describe-instances \
                                --filters "Name=tag:Name,Values=master_instance,worker_instance" \
                                          "Name=instance-state-name,Values=terminated" \
                                --query "length(Reservations[].Instances[])" \
                                --output text || echo "0"
                            ''',
                            returnStdout: true
                        ).trim()
    
                        // Debug output
                        echo "Running instances: ${runningInstances}"
                        echo "Stopped instances: ${stoppedInstances}"
                        echo "Terminated instances: ${terminatedInstances}"
                        echo "Total instances (all states): ${totalInstances}"
    
                        // Determine infrastructure state
                        if ((runningInstances as int) > 0) {
                            currentBuild.description = "Infrastructure state: Instances are running"
                        } else if ((stoppedInstances as int) > 0) {
                            currentBuild.description = "Infrastructure state: Instances are stopped"
                        } else if ((totalInstances as int) == 0 || (terminatedInstances as int) == (totalInstances as int)) {
                            currentBuild.description = "Infrastructure state: No infrastructure existing"
                        } else {
                            currentBuild.description = "Infrastructure state: No infrastructure existing"
                        }
                    } catch (Exception e) {
                        echo "Error detecting infrastructure state: ${e.message}"
                        currentBuild.description = "Infrastructure state: Detection failed"
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