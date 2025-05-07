pipeline {
    agent any
    triggers {
        pollSCM('*/5 * * * *')
    }
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
                        import com.amazonaws.auth.BasicAWSCredentials
                        import com.amazonaws.services.ec2.AmazonEC2Client
                        import com.amazonaws.services.ec2.model.DescribeInstancesRequest
                        import com.amazonaws.services.ec2.model.Filter

                        def awsRegion = binding.hasVariable('AWS_REGION') ? binding.getVariable('AWS_REGION') : 'eu-west-1'
                        def ec2Client = new AmazonEC2Client(new BasicAWSCredentials(
                            System.getenv('AWS_ACCESS_KEY_ID'),
                            System.getenv('AWS_SECRET_ACCESS_KEY')
                        ))
                        ec2Client.setEndpoint("ec2.${awsRegion}.amazonaws.com")

                        def terraformStateExists = new File('/workspace/aws/terraform.tfstate').exists()
                        def runningInstances = ec2Client.describeInstances(new DescribeInstancesRequest().withFilters(
                            new Filter('tag:Name', ['master_instance', 'worker_instance']),
                            new Filter('instance-state-name', ['running', 'pending'])
                        )).reservations*.instances.flatten()
                        def stoppedInstances = ec2Client.describeInstances(new DescribeInstancesRequest().withFilters(
                            new Filter('tag:Name', ['master_instance', 'worker_instance']),
                            new Filter('instance-state-name', ['stopped'])
                        )).reservations*.instances.flatten()

                        def actions = []
                        if (!terraformStateExists) {
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
                        return actions ?: ['No actions available']
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
                            -e \"ansible_ssh_common_args='-o StrictHostKeyChecking=no'\"
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
    post {
        always {
            cleanWs()
        }
    }
}