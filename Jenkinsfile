pipeline {
    agent any
    triggers {
        pollSCM('H/5 * * * *')
    }
    parameters {
        booleanParam(name: 'infrastructureBootstrapping', defaultValue: false, description: 'Set up and create the cloud environment')
        booleanParam(name: 'infrastructureConfiguration', defaultValue: false, description: 'Configure the cloud setup and manage application images')
        booleanParam(name: 'applicationDeployment', defaultValue: false, description: 'Deploy applications to the cloud')
        booleanParam(name: 'destroyInfrastructure', defaultValue: false, description: 'Delete the entire cloud environment')
        string(name: 'DESTROY_CONFIRMATION', defaultValue: '', description: 'Type "destroy" to confirm deletion of the cloud environment')
        string(name: 'AWS_REGION', defaultValue: 'eu-west-1', description: 'AWS region to use (e.g., eu-west-1)')
        string(name: 'LOG_LEVEL', defaultValue: 'INFO', description: 'Log detail level: INFO or DEBUG. Defaults to INFO.')
    }
    stages {
        stage('Infrastructure Bootstrapping') {
            when {
                expression { params['infrastructureBootstrapping'] }
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
                expression { params['infrastructureConfiguration'] }
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
                expression { params['applicationDeployment'] }
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
        stage('Destroy Infrastructure') {
            when {
                allOf {
                    expression { params['destroyInfrastructure'] }
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