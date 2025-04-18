pipeline {
    agent any
    triggers {
        pollSCM('H/5 * * * *')
    }
    parameters {
        booleanParam(name: 'initialise Terraform', defaultValue: false, description: 'Run Terraform Init to initialize the working directory')
        booleanParam(name: 'run Terraform plan', defaultValue: false, description: 'Run Terraform Plan to generate an execution plan')
        booleanParam(name: 'create cluster -tf apply', defaultValue: false, description: 'Run Terraform Apply to apply the planned changes')
        booleanParam(name: 'configure cluster - local registry', defaultValue: false, description: 'Run Ansible playbook to deploy AWS configurations')
        booleanParam(name: 'pull docker images, push to local registry', defaultValue: false, description: 'Run Ansible playbook to deploy Kubernetes and Helm')
        booleanParam(name: 'install helm charts', defaultValue: false, description: 'Run Ansible playbook to deploy Helm charts')
        booleanParam(name: 'destroy the cluster - tf destroy', defaultValue: false, description: 'Run Terraform Destroy to delete all resources')
        string(name: 'DESTROY_CONFIRMATION', defaultValue: '', description: 'Confirm Terraform Destroy by entering "destroy" in this field')
        string(name: 'AWS_REGION', defaultValue: 'eu-west-1', description: 'AWS region for operations (e.g., eu-west-1)')
        string(name: 'LOG_LEVEL', defaultValue: 'INFO', description: 'Log level: INFO or DEBUG. Defaults to INFO if invalid.')
    }
    stages {
        stage('Terraform Init') {
            when {
                expression { params['initialise Terraform'] }
            }
            steps {
                withCredentials([aws(credentialsId: 'aws-access-key-id', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
                    dir("/workspace/aws") {
                        sh 'terraform init'
                    }
                }
            }
        }
        stage('Terraform Plan') {
            when {
                expression { params['run Terraform plan'] }
            }
            steps {
                withCredentials([aws(credentialsId: 'aws-access-key-id', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
                    dir("/workspace/aws") {
                        sh 'terraform plan -out=tfplan'
                    }
                }
            }
        }
        stage('Terraform Apply') {
            when {
                expression { params['create cluster -tf apply'] }
            }
            steps {
                withCredentials([aws(credentialsId: 'aws-access-key-id', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
                    dir("/workspace/aws") {
                        sh 'terraform apply -auto-approve tfplan'
                    }
                }
            }
        }
        stage('Test AWS Inventory') {
            when {
                anyOf {
                    expression { params['configure cluster - local registry'] }
                    expression { params['pull docker images, push to local registry'] }
                    expression { params['install helm charts'] }
                }
            }
            steps {
                withCredentials([aws(credentialsId: 'aws-access-key-id', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
                    dir("/workspace/ansible") {
                        sh "AWS_REGION=eu-west-1 ansible-inventory -i aws_ec2.yaml --list"
                    }
                }
            }
        }
        stage('Configure cluster and local registry') {
            when {
                expression { params['configure cluster - local registry'] }
            }
            steps {
                withCredentials([aws(credentialsId: 'aws-access-key-id', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
                    dir("/workspace/ansible") {
                        sh '''
                           chmod 600 /workspace/aws/id_rsa
                           ansible-playbook -i aws_ec2.yaml aws_playbook.yaml \
                               --private-key=/workspace/aws/id_rsa \
                               -e "ansible_ssh_common_args='-o StrictHostKeyChecking=no'"
                        '''
                    }
                }
            }
        }
        stage('pull ecr images, push to local registry') {
            when {
                expression { params['pull docker images, push to local registry'] }
            }
            steps {
                withCredentials([aws(credentialsId: 'aws-access-key-id', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
                    dir("/workspace/ansible") {
                        sh '''
                           ansible-playbook -i aws_ec2.yaml push_load_playbook.yaml \
                               --private-key=/workspace/aws/id_rsa \
                               -e "ansible_ssh_common_args='-o StrictHostKeyChecking=no'"
                        '''
                    }
                }
            }
        }
        stage('Helm Deploy charts') {
            when {
                expression { params['install helm charts'] }
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
        stage('Terraform Destroy cluster') {
            when {
                allOf {
                    expression { params['destroy the cluster - tf destroy'] }
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