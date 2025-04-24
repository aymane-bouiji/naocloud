pipeline {
    agent any
    triggers {
        pollSCM('*/5 * * * *')
    }
    parameters {
        booleanParam(name: 'Infrastructure Bootstrapping', defaultValue: false, description: 'Set up and create the cloud environment')
        booleanParam(name: 'Infrastructure Configuration', defaultValue: false, description: 'Configure the cloud setup and manage Application images')
        booleanParam(name: 'Application Deployment', defaultValue: false, description: 'Deploy applications to the cloud')
        booleanParam(name: 'Destroy Infrastructure', defaultValue: false, description: 'Delete the entire cloud environment')
        booleanParam(name: 'Stop running Server', defaultValue: false, description: 'Pause (stop) the server')
        booleanParam(name: 'Start Server', defaultValue: false, description: 'Start the stopped server')
        string(name: 'DESTROY_CONFIRMATION', defaultValue: '', description: 'Type "destroy" to confirm deletion of the cloud environment')
        string(name: 'AWS_REGION', defaultValue: 'eu-west-1', description: 'AWS region to use (e.g., eu-west-1)')
        string(name: 'LOG_LEVEL', defaultValue: 'INFO', description: 'Log detail level: INFO or DEBUG. Defaults to INFO.')
    }
    stages {
        stage('Infrastructure Bootstrapping') {
            when {
                expression { params['Infrastructure Bootstrapping'] }
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
                expression { params['Infrastructure Configuration'] }
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
                expression { params['Application Deployment'] }
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
        stage('Stop Server') {
            when {
                expression { params['Stop running Server'] }
            }
            steps {
                withCredentials([[
                    $class: 'AmazonWebServicesCredentialsBinding',
                    credentialsId: 'aws-access-key-id',
                    accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                    secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                ]]) {
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
        }
        stage('Start Server') {
            when {
                expression { params['Start Virtual Machines'] }
            }
            steps {
                withCredentials([aws(credentialsId: 'aws-access-key-id', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
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
        }
        stage('Destroy Infrastructure') {
            when {
                allOf {
                    expression { params['Destroy Infrastructure'] }
                    expression { params.DESTROY_CONFIRMATION == 'destroy' }
                }
            }
            steps {
                withCredentials([aws(credentialsId: 'aws-access-key-id', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
                    dir("/workspace/aws") {
                        sh 'terraform Destroy -auto-approve'
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