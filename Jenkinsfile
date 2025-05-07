pipeline {
    agent any
    triggers {
        pollSCM('*/5 * * * *')
    }
    stages {
        stage('Initialize Credentials') {
            steps {
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-credentials', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                    script {
                        // Ensure credentials are available for Active Choices script
                        env.AWS_ACCESS_KEY_ID = env.AWS_ACCESS_KEY_ID
                        env.AWS_SECRET_ACCESS_KEY = env.AWS_SECRET_ACCESS_KEY
                    }
                }
            }
        }
        stage('Pipeline Execution') {
            parameters {
                string(name: 'AWS_REGION', defaultValue: 'eu-west-1', description: 'AWS region to use (e.g., eu-west-1)')
                string(name: 'LOG_LEVEL', defaultValue: 'INFO', description: 'Log detail level: INFO or DEBUG. Defaults to INFO.')
                string(name: 'CLUSTER_VERSION', defaultValue: '23.09', description: 'Cluster version for naocloud image')
                activeChoice(
                    name: 'ACTION',
                    description: 'Select an action to perform on the cloud infrastructure',
                    script: [
                        $class: 'GroovyScript',
                        script: [
                            sandbox: true,
                            script: '''
                                try {
                                    println "Starting Active Choices script for ACTION parameter"

                                    // Get AWS region
                                    def awsRegion = binding.hasVariable('AWS_REGION') ? binding.getVariable('AWS_REGION') : 'eu-west-1'
                                    println "Using AWS region: ${awsRegion}"

                                    // Check AWS credentials
                                    def accessKey = System.getenv('AWS_ACCESS_KEY_ID')
                                    def secretKey = System.getenv('AWS_SECRET_ACCESS_KEY')
                                    if (!accessKey || !secretKey) {
                                        println "Error: AWS credentials not found in environment"
                                        return ['No actions available']
                                    }
                                    println "AWS credentials found"

                                    // Check running instances
                                    def runningInstances = []
                                    try {
                                        def cmd = "aws ec2 describe-instances --region ${awsRegion} --filters \\"Name=tag:Name,Values=master_instance,worker_instance\\" \\"Name=instance-state-name,Values=running,pending\\" --query \\"Reservations[].Instances[].InstanceId\\" --output text"
                                        def result = cmd.execute().text.trim()
                                        runningInstances = result ? result.split('\n') : []
                                        println "Running instances: ${runningInstances}"
                                    } catch (Exception e) {
                                        println "Error checking running instances: ${e.message}"
                                    }

                                    // Check stopped instances
                                    def stoppedInstances = []
                                    try {
                                        def cmd = "aws ec2 describe-instances --region ${awsRegion} --filters \\"Name=tag:Name,Values=master_instance,worker_instance\\" \\"Name=instance-state-name,Values=stopped\\" --query \\"Reservations[].Instances[].InstanceId\\" --output text"
                                        def result = cmd.execute().text.trim()
                                        stoppedInstances = result ? result.split('\n') : []
                                        println "Stopped instances: ${stoppedInstances}"
                                    } catch (Exception e) {
                                        println "Error checking stopped instances: ${e.message}"
                                    }

                                    // Build action choices
                                    def actions = []
                                    if (runningInstances.isEmpty() && stoppedInstances.isEmpty()) {
                                        actions.add('Infrastructure Bootstrapping')
                                    } else {
                                        actions.addAll(['Infrastructure Configuration', 'Application Deployment', 'Destroy Infrastructure'])
                                        if (!runningInstances.isEmpty()) {
                                            actions.addAll(['Stop running Server', 'Display Addresses'])
                                        }
                                        if (!stoppedInstances.isEmpty()) {
                                            actions.add('Start Server')
                                        }
                                    }
                                    println "Generated actions: ${actions}"
                                    return actions ?: ['No actions available']
                                } catch (Exception e) {
                                    println "Critical error in Active Choices script: ${e.message}"
                                    return ['Error: Check Jenkins logs']
                                }
                            '''
                        ]
                    ]
                )
                string(name: 'DESTROY_CONFIRMATION', defaultValue: '', description: 'Type "destroy" to confirm deletion of the cloud environment')
            }
            stages {
                stage('Parameter Validation') {
                    steps {
                        script {
                            echo "Validating parameters for action: ${params.ACTION}"
                            if (params.ACTION in ['No actions available', 'Error: Check Jenkins logs']) {
                                error("❌ Invalid action selected: ${params.ACTION}. Check AWS configuration or infrastructure state.")
                            }
                            if (params.ACTION == 'Destroy Infrastructure' && params.DESTROY_CONFIRMATION != 'destroy') {
                                error("❌ Destroy confirmation not provided. Please type 'destroy' in DESTROY_CONFIRMATION to proceed.")
                            }
                        }
                    }
                }
                stage('Infrastructure Bootstrapping') {
                    when {
                        expression { params.ACTION == 'Infrastructure Bootstrapping' }
                    }
                    steps {
                        dir('/workspace/aws') {
                            sh 'terraform init'
                            sh 'terraform plan -out=tfplan'
                            sh 'terraform apply -auto-approve tfplan'
                        }
                    }
                }
                stage('Infrastructure Configuration') {
                    when {
                        expression { params.ACTION == 'Infrastructure Configuration' }
                    }
                    steps {
                        dir('/workspace/ansible') {
                            sh "AWS_REGION=${params.AWS_REGION} ansible-inventory -i aws_ec2.yaml --list"
                            sh """
                                ansible-playbook -i aws_ec2.yaml aws_playbook.yaml \
                                    --private-key=/workspace/aws/id_rsa \
                                    -e "ansible_ssh_common_args='-o StrictHostKeyChecking=no'"
                            """
                            sh """
                                ansible-playbook -i aws_ec2.yaml push_load_playbook-1.yaml \
                                    --private-key=/workspace/aws/id_rsa \
                                    -e "ansible_ssh_common_args='-o StrictHostKeyChecking=no'" \
                                    -e "naocloud_tag=${params.CLUSTER_VERSION}" \
                                    -e "naogizmo_tag=${params.CLUSTER_VERSION}"
                            """
                        }
                    }
                }
                stage('Application Deployment') {
                    when {
                        expression { params.ACTION == 'Application Deployment' }
                    }
                    steps {
                        dir('/workspace/ansible') {
                            sh """
                                ansible-playbook -i aws_ec2.yaml helm-playbook.yaml \
                                    --private-key=/workspace/aws/id_rsa \
                                    -e "ansible_ssh_common_args='-o StrictHostKeyChecking=no'"
                            """
                        }
                    }
                }
                stage('Stop Server') {
                    when {
                        expression { params.ACTION == 'Stop running Server' }
                    }
                    steps {
                        sh '''
                            export AWS_DEFAULT_REGION=${AWS_REGION}
                            MASTER_IDS=$(aws ec2 describe-instances \
                                --filters "Name=tag:Name,Values=master_instance" "Name=instance-state-name,Values=running,pending" \
                                --query "Reservations[].Instances[].InstanceId" --output text)
                            WORKER_IDS=$(aws ec2 describe-instances \
                                --filters "Name=tag:Name,Values=worker_instance" "Name=instance-state-name,Values=running,pending" \
                                --query "Reservations[].Instances[].InstanceId" --output text)
                            INSTANCE_IDS="$MASTER_IDS $WORKER_IDS"
                            if [ -n "$INSTANCE_IDS" ]; then
                                echo "Stopping instances: $INSTANCE_IDS"
                                aws ec2 stop-instances --instance-ids $INSTANCE_IDS
                            else
                                echo "No running instances found."
                            fi
                        '''
                    }
                }
                stage('Start Server') {
                    when {
                        expression { params.ACTION == 'Start Server' }
                    }
                    steps {
                        sh '''
                            export AWS_DEFAULT_REGION=${AWS_REGION}
                            MASTER_IDS=$(aws ec2 describe-instances \
                                --filters "Name=tag:Name,Values=master_instance" "Name=instance-state-name,Values=stopped" \
                                --query "Reservations[].Instances[].InstanceId" --output text)
                            WORKER_IDS=$(aws ec2 describe-instances \
                                --filters "Name=tag:Name,Values=worker_instance" "Name=instance-state-name,Values=stopped" \
                                --query "Reservations[].Instances[].InstanceId" --output text)
                            INSTANCE_IDS="$MASTER_IDS $WORKER_IDS"
                            if [ -n "$INSTANCE_IDS" ]; then
                                echo "Starting instances: $INSTANCE_IDS"
                                aws ec2 start-instances --instance-ids $INSTANCE_IDS
                            else
                                echo "No stopped instances found."
                            fi
                        '''
                    }
                }
                stage('Destroy Infrastructure') {
                    when {
                        allOf {
                            expression { params.ACTION == 'Destroy Infrastructure' }
                            expression { params.DESTROY_CONFIRMATION == 'destroy' }
                        }
                    }
                    steps {
                        dir('/workspace/aws') {
                            sh 'terraform destroy -auto-approve'
                        }
                    }
                }
                stage('Display Addresses') {
                    when {
                        expression { params.ACTION == 'Display Addresses' }
                    }
                    steps {
                        sh '''
                            set +x
                            export AWS_DEFAULT_REGION=${AWS_REGION}
                            echo "=== Worker Instance Public Addresses ==="
                            WORKER_DATA=$(aws ec2 describe-instances \
                                --filters "Name=tag:Name,Values=worker_instance" "Name=instance-state-name,Values=running,pending" \
                                --query "Reservations[].Instances[].[PublicIpAddress,PublicDnsName]" --output text)
                            if [ -n "$WORKER_DATA" ]; then
                                i=0
                                echo "$WORKER_DATA" | while read -r ip dns; do
                                    echo "Worker $((i+=1)):"
                                    echo "  Public IP: $ip"
                                    echo "  Public DNS: $dns"
                                done
                            else
                                echo "No running worker instances found."
                            fi
                        '''
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