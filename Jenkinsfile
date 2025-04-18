pipeline {
    agent any
    environment {
        WORKSPACE_DIR = '/workspace'
        AWS_REGION = "${params.AWS_REGION}"

    }
    parameters {
        booleanParam(name: 'RUN_TERRAFORM_INIT', defaultValue: false, description: 'Run Terraform Init to initialize the working directory')
        booleanParam(name: 'RUN_TERRAFORM_PLAN', defaultValue: false, description: 'Run Terraform Plan to generate an execution plan')
        booleanParam(name: 'RUN_TERRAFORM_APPLY', defaultValue: false, description: 'Run Terraform Apply to apply the planned changes')
        booleanParam(name: 'RUN_ANSIBLE_DEPLOY', defaultValue: false, description: 'Run Ansible playbook to deploy Kubernetes and Helm')
        booleanParam(name: 'RUN_HELM_DEPLOY', defaultValue: false, description: 'Run Ansible playbook to deploy Helm charts')
        booleanParam(name: 'RUN_TERRAFORM_DESTROY', defaultValue: false, description: 'Run Terraform Destroy to delete all resources')
        string(name: 'DESTROY_CONFIRMATION', defaultValue: '', description: 'Confirm Terraform Destroy by entering "destroy" in this field')
        string(name: 'AWS_REGION', defaultValue: 'eu-west-1', description: 'AWS region for operations (e.g., eu-west-1)')
        string(name: 'LOG_LEVEL', defaultValue: 'INFO', description: 'Log level: INFO or DEBUG. Defaults to INFO if invalid.')
    }
    stages {
        stage('Terraform Init') {
            when {
                expression { params.RUN_TERRAFORM_INIT }
            }
            steps {
                withCredentials([aws(credentialsId: 'aws-access-key-id', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
                    dir('/workspace/aws') {
                        sh 'terraform init'
                    }
                }
            }
        }
        stage('Terraform Plan') {
            when {
                expression { params.RUN_TERRAFORM_PLAN }
            }
            steps {
                withCredentials([aws(credentialsId: 'aws-access-key-id', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
                    dir('/workspace/aws') {
                        sh 'terraform plan -out=tfplan'
                    }
                }
            }
        }
        stage('Terraform Apply') {
            when {
                expression { params.RUN_TERRAFORM_APPLY }
            }
            steps {
                withCredentials([aws(credentialsId: 'aws-access-key-id', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
                    dir('/workspace/aws') {
                        sh 'terraform apply -auto-approve tfplan'
                    }
                }
            }
        }
        stage('Test AWS Inventory') {
            when {
                anyOf {
                    expression { params.RUN_ANSIBLE_DEPLOY }
                    expression { params.RUN_HELM_DEPLOY }
                }
            }
            steps {
                withCredentials([aws(credentialsId: 'aws-access-key-id', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
                    dir('/workspace/ansible') {
                        sh "AWS_REGION=${params.AWS_REGION} ansible-inventory -i aws_ec2.yaml --list"
                    }
                }
            }
        }
        stage('Ansible Deploy K8s and Helm') {
            when {
                expression { params.RUN_ANSIBLE_DEPLOY }
            }
            steps {
                withCredentials([aws(credentialsId: 'aws-access-key-id', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
                    dir('/workspace/ansible') {
                        sh '''
                            ansible-playbook -i aws_ec2.yaml push_load_playbook.yaml \
                                --private-key=/workspace/aws/id_rsa \
                                -e "ansible_ssh_common_args='-o StrictHostKeyChecking=no'"
                        '''
                    }
                }
            }
        }
        stage('Helm Deploy') {
            when {
                expression { params.RUN_HELM_DEPLOY }
            }
            steps {
                withCredentials([aws(credentialsId: 'aws-access-key-id', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
                    dir('/workspace/ansible') {
                        sh '''
                            ansible-playbook -i aws_ec2.yaml helm-playbook.yaml \
                                --private-key=/workspace/aws/id_rsa \
                                -e "ansible_ssh_common_args='-o StrictHostKeyChecking=no'"
                        '''
                    }
                }
            }
        }
        stage('Terraform Destroy') {
            when {
                allOf {
                    expression { params.RUN_TERRAFORM_DESTROY }
                    expression { params.DESTROY_CONFIRMATION == 'destroy' }
                }
            }
            steps {
                withCredentials([aws(credentialsId: 'aws-access-key-id', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
                    dir('/workspace/aws') {
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