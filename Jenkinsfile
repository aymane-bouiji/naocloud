pipeline {
    agent any
    
    triggers {
        pollSCM('*/5 * * * *')
    }
    
    parameters {
        string(name: 'AWS_REGION', defaultValue: 'eu-west-1', description: 'AWS region to use (e.g., eu-west-1)')
        string(name: 'LOG_LEVEL', defaultValue: 'INFO', description: 'Log detail level: INFO or DEBUG. Defaults to INFO.')
        string(name: 'CLUSTER_VERSION', defaultValue: '23.09', description: 'Cluster version for naocloud image')
        // Active Choices parameter for multiple selections
        activeChoice(name: 'ACTION', description: 'Select actions to perform (can select multiple)', script: '''
            def choices = ['Detect Infrastructure State']
            return choices
        ''', choiceType: 'CHECKBOX')
        string(name: 'DESTROY_CONFIRMATION', defaultValue: '', description: 'Type "destroy" to confirm deletion (required for Destroy Infrastructure)')
    }
    
    stages {
        stage('Detect Infrastructure State') {
            when {
                expression { 'Detect Infrastructure State' in params.ACTION.tokenize(',') }
            }
            steps {
                script {
                    env.AWS_DEFAULT_REGION = params.AWS_REGION
                    
                    echo "Detecting infrastructure state..."
                    
                    // Check for Terraform state
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
                    
                    // Determine instance states
                    def instancesRunning = false
                    def instancesStopped = false
                    try {
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
                        echo "- Terraform state exists: ${terraformStateExists}"
                        echo "- Running instances: ${instancesRunning}"
                        echo "- Stopped instances: ${instancesStopped}"
                    } catch (Exception e) {
                        echo "Error detecting instance state: ${e.message}"
                        instancesRunning = false
                        instancesStopped = false
                    }
                    
                    // Dynamic action choices
                    def actionChoices = []
                    if (!terraformStateExists) {
                        actionChoices.add("Infrastructure Bootstrapping")
                    } else {
                        actionChoices.add("Infrastructure Configuration")
                        actionChoices.add("Application Deployment")
                        actionChoices.add("Destroy Infrastructure")
                        if (instancesRunning) {
                            actionChoices.add("Stop running Server")
                            actionChoices.add("Display Addresses")
                        }
                        if (instancesStopped) {
                            actionChoices.add("Start Server")
                        }
                    }
                    
                    if (actionChoices.isEmpty()) {
                        actionChoices.add("No actions available")
                    }
                    
                    // Update ACTION parameter dynamically
                    def dynamicParams = []
                    dynamicParams.add(string(name: 'AWS_REGION', defaultValue: params.AWS_REGION, 
                                           description: 'AWS region to use (e.g., eu-west-1)'))
                    dynamicParams.add(string(name: 'LOG_LEVEL', defaultValue: params.LOG_LEVEL, 
                                          description: 'Log detail level: INFO or DEBUG. Defaults to INFO.'))
                    dynamicParams.add(string(name: 'CLUSTER_VERSION', defaultValue: params.CLUSTER_VERSION, 
                                          description: 'Cluster version for naocloud image'))
                    dynamicParams.add(activeChoice(name: 'ACTION', description: 'Select actions to perform (can select multiple)', 
                                                 script: "return ${actionChoices.inspect()}", choiceType: 'CHECKBOX'))
                    if (actionChoices.contains("Destroy Infrastructure")) {
                        dynamicParams.add(string(name: 'DESTROY_CONFIRMATION', defaultValue: '', 
                                               description: 'Type "destroy" to confirm deletion'))
                    }
                    
                    properties([parameters(dynamicParams)])
                    
                    // Exit gracefully
                    currentBuild.description = "Infrastructure detection complete. Please run the build again with your selected actions."
                    echo "Detection complete. Please rerun the build to select actions."
                    return
                }
            }
        }
        
        stage('Parameter Validation') {
            when {
                expression { !('Detect Infrastructure State' in params.ACTION.tokenize(',')) }
            }
            steps {
                script {
                    def actions = params.ACTION.tokenize(',')
                    echo "Validating parameters for actions: ${actions}"
                    if (actions.contains("Destroy Infrastructure") && params.DESTROY_CONFIRMATION != 'destroy') {
                        error("❌ Destroy confirmation not provided. Please type 'destroy' in DESTROY_CONFIRMATION to proceed.")
                    }
                }
            }
        }

        stage('Execute Actions') {
            when {
                expression { !('Detect Infrastructure State' in params.ACTION.tokenize(',')) }
            }
            steps {
                script {
                    def actions = params.ACTION.tokenize(',')
                    for (def action in actions) {
                        echo "Executing action: ${action}"
                        
                        if (action == "Infrastructure Bootstrapping") {
                            dir("/workspace/aws") {
                                sh 'terraform init'
                                sh 'terraform plan -out=tfplan'
                                sh 'terraform apply -auto-approve tfplan'
                            }
                        }
                        else if (action == "Infrastructure Configuration") {
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
                        else if (action == "Application Deployment") {
                            dir("/workspace/ansible") {
                                sh """
                                    ansible-playbook -i aws_ec2.yaml helm-playbook.yaml \
                                        --private-key=/workspace/aws/id_rsa \
                                        -e \"ansible_ssh_common_args='-o StrictHostKeyChecking=no'\" 
                                """
                            }
                        }
                        else if (action == "Stop running Server") {
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
                        else if (action == "Start Server") {
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
                        else if (action == "Destroy Infrastructure") {
                            dir("/workspace/aws") {
                                sh 'terraform destroy -auto-approve'
                            }
                        }
                        else if (action == "Display Addresses") {
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
            }
        }
    }
    
    post {
        always {
            cleanWs()
        }
    }
}