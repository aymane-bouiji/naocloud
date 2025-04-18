pipeline {
    agent any
    environment {
        WORKSPACE_DIR = '/workspace'
        AWS_REGION = "${params.AWS_REGION}"
    }
    parameters {
        booleanParam(name: 'initialise Terraform', defaultValue: false, description: 'Run Terraform Init to initialize the working directory')
        booleanParam(name: 'run Terraform plan', defaultValue: false, description: 'Run Terraform Plan to generate an execution plan')
        booleanParam(name: 'create the  cluster infrastructure (EC2 VMs) - tf apply', defaultValue: false, description: 'Run Terraform Apply to apply the planned changes')
        booleanParam(name: 'install and configure k8s components - add local registry', defaultValue: false, description: 'Run Ansible playbook to deploy AWS configurations')
        booleanParam(name: 'pull docker images, push to local registry on master', defaultValue: false, description: 'Run Ansible playbook to deploy Kubernetes and Helm')
        booleanParam(name: 'install helm charts ', defaultValue: false, description: 'Run Ansible playbook to deploy Helm charts')
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
                    dir("${env.WORKSPACE_DIR}/aws") {
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
                    dir("${env.WORKSPACE_DIR}/aws") {
                        sh 'terraform plan -out=tfplan'
                    }
                }
            }
        }
        stage('Terraform Apply') {
            when {
                expression { params['create the infrastructure (EC2 VMs) - tf apply'] }
            }
            steps {
                withCredentials([aws(credentialsId: 'aws-access-key-id', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
                    dir("${env.WORKSPACE_DIR}/aws") {
                        sh 'terraform apply -auto-approve tfplan'
                    }
                }
            }
        }
        stage('Test AWS Inventory') {
            when {
                anyOf {
                    expression { params.RUN_ANSIBLE_AWS_DEPLOY }
                    expression { params.RUN_ANSIBLE_DEPLOY }
                    expression { params.RUN_HELM_DEPLOY }
                }
            }
            steps {
                withCredentials([aws(credentialsId: 'aws-access-key-id', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
                    dir("${env.WORKSPACE_DIR}/ansible") {
                        sh "AWS_REGION=${env.AWS_REGION} ansible-inventory -i aws_ec2.yaml --list"
                    }
                }
            }
        }
        stage('Configure cluster and local registry') {
            when {
                expression { params.RUN_ANSIBLE_AWS_DEPLOY }
            }
            steps {
                withCredentials([aws(credentialsId: 'aws-access-key-id', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
                    dir("${env.WORKSPACE_DIR}/ansible") {
                        sh '''
                           chmod 600 /workspace/aws/id_rsa 


                            ansible-playbook -i aws_ec2.yaml aws_playbook.yaml \
                                --private-key=${env.WORKSPACE_DIR}/aws/id_rsa \
                                -e "ansible_ssh_common_args='-o StrictHostKeyChecking=no'"
                        '''
                    }
                }
            }
        }
        stage('pull ecr images, push to local registry') {
            when {
                expression { params.RUN_ANSIBLE_DEPLOY }
            }
            steps {
                withCredentials([aws(credentialsId: 'aws-access-key-id', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
                    dir("${env.WORKSPACE_DIR}/ansible") {
                        sh '''
                            ansible-playbook -i aws_ec2.yaml push_load_playbook.yaml \
                                --private-key=${env.WORKSPACE_DIR}/aws/id_rsa \
                                -e "ansible_ssh_common_args='-o StrictHostKeyChecking=no'"
                        '''
                    }
                }
            }
        }
        stage('Helm Deploy charts ') {
            when {
                expression { params.RUN_HELM_DEPLOY }
            }
            steps {
                withCredentials([aws(credentialsId: 'aws-access-key-id', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
                    dir("${env.WORKSPACE_DIR}/ansible") {
                        sh '''
                            ansible-playbook -i aws_ec2.yaml helm-playbook.yaml \
                                --private-key=${env.WORKSPACE_DIR}/aws/id_rsa \
                                -e "ansible_ssh_common_args='-o StrictHostKeyChecking=no'"
                        '''
                    }
                }
            }
        }
        stage('Terraform Destroy cluster') {
            when {
                allOf {
                    expression { params.RUN_TERRAFORM_DESTROY }
                    expression { params.DESTROY_CONFIRMATION == 'destroy' }
                }
            }
            steps {
                withCredentials([aws(credentialsId: 'aws-access-key-id', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
                    dir("${env.WORKSPACE_DIR}/aws") {
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