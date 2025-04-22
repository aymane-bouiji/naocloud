pipeline {
    agent any
    triggers {
        pollSCM('H/5 * * * *')
    }
    parameters {
        booleanParam(name: 'infrastructure Bootstrapping', defaultValue: false, description: 'Set up and create the cloud environment')
        booleanParam(name: 'infrastructure Configuration', defaultValue: false, description: 'Configure the cloud setup and manage application images')
        booleanParam(name: 'application Deployment', defaultValue: false, description: 'Deploy applications to the cloud')
        booleanParam(name: 'destroy Infrastructure', defaultValue: false, description: 'Delete the entire cloud environment')
        booleanParam(name: 'pause Virtual Machines', defaultValue: false, description: 'Pause (stop) the EC2 virtual machines')
        string(name: 'DESTROY_CONFIRMATION', defaultValue: '', description: 'Type "destroy" to confirm deletion of the cloud environment')
        string(name: 'AWS_REGION', defaultValue: 'eu-west-1', description: 'AWS region to use (e.g., eu-west-1)')
        string(name: 'LOG_LEVEL', defaultValue: 'INFO', description: 'Log detail level: INFO or DEBUG. Defaults to INFO.')
    }
    stages {
        stage('Infrastructure Bootstrapping') {
            when {
                expression { params['infrastructure Bootstrapping'] }
            }
            steps {
                withCredentials([aws(credentialsId: 'aws-access-key-id', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
                    dir("/workspace/aws") {
                        sh 'terraform init'
                        sh 'terraform plan -out=tfplan'
                        sh 'terraform apply -auto-approve tfplan'
                    }
                }
            }
        }
        stage('Infrastructure Configuration') {
            when {
                expression { params['infrastructure Configuration'] }
            }
            steps {
                withCredentials([aws(credentialsId: 'aws-access-key-id', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
                    dir("/workspace/ansible") {
                        sh "AWS_REGION=${params.AWS_REGION} ansible-inventory -i aws_ec2.yaml --list"
                        sh '''
                           ansible-playbook -i aws_ec2.yaml aws_playbook.yaml \
                               --private-key=/workspace/aws/id_rsa \
                               -e "ansible_ssh_common_args='-o StrictHostKeyChecking=no'"
                        '''
                        sh '''
                           ansible-playbook -i aws_ec2.yaml push_load_playbook.yaml \
                               --private-key=/workspace/aws/id_rsa \
                               -e "ansible_ssh_common_args='-o StrictHostKeyChecking=no'"
                        '''
                    }
                }
            }
        }
        stage('Application Deployment') {
            when {
                expression { params['application Deployment'] }
            }
            steps {
                withCredentials([aws(credentialsId: 'aws-access-key-id', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
                    dir("/workspace/ansible") {
                        sh '''
                           ansible-playbook -i aws_ec2.yaml helm-playbook.yaml \
                               --private-key=/workspace/aws/id_rsa \
                               -e "ansible_ssh_common_args='-o StrictHostKeyChecking=no'"
                        '''
                    }
                }
            }
        }
        stage('Stop EC2 Instances') {
            when {
                expression { params['pause Virtual Machines'] }
            }
            steps {
                withCredentials([[
                    $class: 'AmazonWebServicesCredentialsBinding',
                    credentialsId: 'aws-access-key-id',
                    accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                    secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                ]]) {
                    sh '''
                        # Set AWS region from parameter
                        export AWS_DEFAULT_REGION=${AWS_REGION}

                        # Get instance IDs for master_instance
                        MASTER_IDS=$(aws ec2 describe-instances \\
                            --filters "Name=tag:Name,Values=master_instance" "Name=instance-state-name,Values=running,pending" \\
                            --query "Reservations[].Instances[].InstanceId" \\
                            --output text)

                        # Get instance IDs for worker_instance
                        WORKER_IDS=$(aws ec2 describe-instances \\
                            --filters "Name=tag:Name,Values=worker_instance" "Name=instance-state-name,Values=running,pending" \\
                            --query "Reservations[].Instances[].InstanceId" \\
                            --output text)

                        # Combine instance IDs
                        INSTANCE_IDS="$MASTER_IDS $WORKER_IDS"

                        # Stop instances if any are found
                        if [ -n "$INSTANCE_IDS" ]; then
                            echo "Stopping instances: $INSTANCE_IDS"
                            aws ec2 stop-instances --instance-ids $INSTANCE_IDS
                        else
                            echo "No running instances found with tags Name=master_instance or Name=worker_instance"
                        fi
                    '''
                }
            }
        }
        stage('Destroy Infrastructure') {
            when {
                allOf {
                    expression { params['destroy Infrastructure'] }
                    expression { params.DESTROY_CONFIRMATION == 'destroy' }
                }
            }
            steps {
                withCredentials([aws(credentialsId: 'aws-access-key-id', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
                    dir("/workspace/aws") {
                        sh 'terraform destroy -auto-approve'
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