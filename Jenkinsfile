pipeline {
    agent any
    triggers {
        pollSCM('H/5 * * * *')
    }
    parameters {
        booleanParam(name: 'infrastructure_bootstrapping', defaultValue: false, description: 'Run Terraform Init, Plan, and Apply to initialize and create infrastructure')
        booleanParam(name: 'infrastructure_configuration', defaultValue: false, description: 'Run Ansible playbooks to configure cluster, local registry, and manage Docker images')
        booleanParam(name: 'application_deployment', defaultValue: false, description: 'Run Ansible playbook to deploy Helm charts')
        booleanParam(name: 'destroy_infrastructure', defaultValue: false, description: 'Run Terraform Destroy to delete all resources')
        string(name: 'DESTROY_CONFIRMATION', defaultValue: '', description: 'Confirm Terraform Destroy by entering "destroy" in this field')
        string(name: 'AWS_REGION', defaultValue: 'eu-west-1', description: 'AWS region for operations (e.g., eu-west-1)')
        string(name: 'LOG_LEVEL', defaultValue: 'INFO', description: 'Log level: INFO or DEBUG. Defaults to INFO if invalid.')
    }
    stages {
        stage('Infrastructure Bootstrapping') {
            when {
                expression { params['infrastructure_bootstrapping'] }
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
                expression { params['infrastructure_configuration'] }
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
                expression { params['application_deployment'] }
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
                    expression { params['destroy_infrastructure'] }
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