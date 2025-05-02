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
        booleanParam(name: 'Display Addresses', defaultValue: false, description: 'Display addresses of running server')
        string(name: 'DESTROY_CONFIRMATION', defaultValue: '', description: 'Type "destroy" to confirm deletion of the cloud environment')
        string(name: 'AWS_REGION', defaultValue: 'eu-west-1', description: 'AWS region to use (e.g., eu-west-1)')
        string(name: 'LOG_LEVEL', defaultValue: 'INFO', description: 'Log detail level: INFO or DEBUG. Defaults to INFO.')
        string(name: 'CLUSTER_VERSION', defaultValue: '23.09', description: 'Cluster version for naocloud image')
    }
    stages {
        stage('Parameter Validation') {
            steps {
                script {
                    if (params['Start Server'] && params['Stop running Server']) {
                        error("❌ You cannot start and stop the server at the same time. Please select only one.")
                    }
                    if (params['Infrastructure Bootstrapping'] && params['Destroy Infrastructure']) {
                        error("❌ You cannot bootstrap and destroy infrastructure at the same time. Please select only one.")
                    }
                    if (params['Destroy Infrastructure'] && (
                        params['Infrastructure Configuration'] ||
                        params['Application Deployment'] ||
                        params['Start Server'] ||
                        params['Stop running Server'] ||
                        params['Display Addresses']
                    )) {
                        error("❌ Cannot perform other actions while destroying infrastructure. Please deselect all except 'Destroy Infrastructure'.")
                    }
                    if (params['Destroy Infrastructure'] && params.DESTROY_CONFIRMATION != 'destroy') {
                        error("❌ Destroy confirmation not provided. Please type 'destroy' in DESTROY_CONFIRMATION to proceed.")
                    }
                    def actions = [
                        params['Infrastructure Bootstrapping'],
                        params['Infrastructure Configuration'],
                        params['Application Deployment'],
                        params['Destroy Infrastructure'],
                        params['Start Server'],
                        params['Stop running Server'],
                        params['Display Addresses']
                    ]
                    if (!actions.any { it }) {
                        error("⚠️ No action selected. Please choose at least one operation.")
                    }

                    // Server state validation
                    def masterData = sh(script: """
                        aws ec2 describe-instances \
                            --region ${params.AWS_REGION} \
                            --filters "Name=tag:Name,Values=master_instance" \
                            --query "Reservations[].Instances[].State.Name" \
                            --output text
                    """, returnStdout: true).trim()
                    def workerData = sh(script: """
                        aws ec2 describe-instances \
                            --region ${params.AWS_REGION} \
                            --filters "Name=tag:Name,Values=worker_instance" \
                            --query "Reservations[].Instances[].State.Name" \
                            --output text
                    """, returnStdout: true).trim()
                    def states = (masterData.tokenize() + workerData.tokenize()).unique()

                    if (params['Infrastructure Bootstrapping'] && states && !states.contains('terminated')) {
                        error("❌ Cannot bootstrap infrastructure: instances already exist in states ${states}. Consider destroying first.")
                    }
                    if (params['Start Server'] && !states.contains('stopped')) {
                        error("❌ Cannot start server: no stopped instances found. Current states: ${states ?: 'none'}.")
                    }
                    if (params['Stop running Server'] && !states.any { it in ['running', 'pending'] }) {
                        error("❌ Cannot stop server: no running or pending instances found. Current states: ${states ?: 'none'}.")
                    }
                    if ((params['Infrastructure Configuration'] || params['Application Deployment'] || params['Display Addresses']) && (!states || states.contains('terminated'))) {
                        error("❌ Cannot perform ${params['Infrastructure Configuration'] ? 'configuration' : params['Application Deployment'] ? 'deployment' : 'display addresses'}: no active instances found. Current states: ${states ?: 'none'}.")
                    }
                }
            }
        }
        stage('Display Server State') {
            steps {
                sh '''
                    set +x
                    export AWS_DEFAULT_REGION=${AWS_REGION}
                    echo "=== Server State ==="
                    
                    echo "Master Instances:"
                    MASTER_DATA=$(aws ec2 describe-instances \
                        --filters "Name=tag:Name,Values=master_instance" \
                        --query "Reservations[].Instances[].[InstanceId,State.Name]" \
                        --output text)
                    if [ -n "$MASTER_DATA" ]; then
                        echo "$MASTER_DATA" | while read -r id state; do
                            echo "  Instance ID: $id, State: $state"
                        done
                    else
                        echo "  No master instances found with tag Name=master_instance"
                    fi
                    
                    echo "Worker Instances:"
                    WORKER_DATA=$(aws ec2 describe-instances \
                        --filters "Name=tag:Name,Values=worker_instance" \
                        --query "Reservations[].Instances[].[InstanceId,State.Name]" \
                        --output text)
                    if [ -n "$WORKER_DATA" ]; then
                        echo "$WORKER_DATA" | while read -r id state; do
                            echo "  Instance ID: $id, State: $state"
                        done
                    else
                        echo "  No worker instances found with tag Name=worker_instance"
                    fi
                    
                    echo "==================="
                '''
            }
        }
        stage('Infrastructure Bootstrapping') {
            when {
                expression { params['Infrastructure Bootstrapping'] }
            }
            steps {
                dir("/workspace/aws") {
                    sh 'terraform init'
                    sh 'terraform plan -out=tfplan'
                    sh 'terraform apply -auto-approve tfplan'
                }
            }
        }
        stage('Infrastructure Configuration') {
            when {
                expression { params['Infrastructure Configuration'] }
            }
            steps {
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
        }
        stage('Application Deployment') {
            when {
                expression { params['Application Deployment'] }
            }
            steps {
                dir("/workspace/ansible") {
                    sh """
                        ansible-playbook -i aws_ec2.yaml helm-playbook.yaml  \
                            --private-key=/workspace/aws/id_rsa \
                            -e \"ansible_ssh_common_args='-o StrictHostKeyChecking=no'\" 
                    """
                }
            }
        }
        stage('Stop Server') {
            when {
                expression { params['Stop running Server'] }
            }
            steps {
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
        stage('Start Server') {
            when {
                expression { params['Start Server'] }
            }
            steps {
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
        stage('Destroy Infrastructure') {
            when {
                allOf {
                    expression { params['Destroy Infrastructure'] }
                    expression { params.DESTROY_CONFIRMATION == 'destroy' }
                }
            }
            steps {
                dir("/workspace/aws") {
                    sh 'terraform destroy -auto-approve'
                }
            }
        }
        stage('Display Addresses') {
            when {
                expression { params['Display Addresses'] }
            }
            steps {
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
    post {
        always {
            cleanWs()
        }
    }
}