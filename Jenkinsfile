def getServerState() {
    def masterState = sh(script: '''
        export AWS_DEFAULT_REGION=${AWS_REGION:-eu-west-1}
        aws ec2 describe-instances \
            --filters "Name=tag:Name,Values=master_instance" \
            --query "Reservations[].Instances[].[State.Name]" \
            --output text
    ''', returnStdout: true).trim()
    
    def workerState = sh(script: '''
        export AWS_DEFAULT_REGION=${AWS_REGION:-eu-west-1}
        aws ec2 describe-instances \
            --filters "Name=tag:Name,Values=worker_instance" \
            --query "Reservations[].Instances[].[State.Name]" \
            --output text
    ''', returnStdout: true).trim()
    
    return [masterState: masterState, workerState: workerState]
}

properties([
    parameters([
        string(name: 'AWS_REGION', defaultValue: 'eu-west-1', description: 'AWS region to use (e.g., eu-west-1)'),
        string(name: 'LOG_LEVEL', defaultValue: 'INFO', description: 'Log detail level: INFO or DEBUG. Defaults to INFO.'),
        string(name: 'CLUSTER_VERSION', defaultValue: '23.09', description: 'Cluster version for naocloud image'),
        // Dynamic parameters with Active Choices
        activeChoiceReactiveParam(
            name: 'ACTION',
            description: 'Select an action to perform',
            groovyScript: '''
                // Get server state first
                def serverState = getServerState()
                def masterState = serverState.masterState
                def workerState = serverState.workerState
                
                // Define possible actions based on server state
                def actions = []
                
                // If no instances exist
                if (masterState == '' && workerState == '') {
                    actions.add('Infrastructure Bootstrapping')
                } else {
                    // If instances exist but are stopped
                    if (masterState == 'stopped' || workerState == 'stopped') {
                        actions.add('Start Server')
                    }
                    
                    // If instances are running
                    if (masterState == 'running' || workerState == 'running') {
                        actions.add('Infrastructure Configuration')
                        actions.add('Application Deployment')
                        actions.add('Stop running Server')
                        actions.add('Display Addresses')
                    }
                    
                    // Always offer destroy option if instances exist
                    actions.add('Destroy Infrastructure')
                }
                
                return actions
            '''
        ),
        // Conditional parameters that appear based on selected action
        activeChoiceReactiveParam(
            name: 'DESTROY_CONFIRMATION',
            description: 'Type "destroy" to confirm deletion of the cloud environment',
            referencedParameters: 'ACTION',
            groovyScript: '''
                if (ACTION == 'Destroy Infrastructure') {
                    return ['']
                } else {
                    return []
                }
            '''
        )
    ]),
    pipelineTriggers([
        pollSCM('*/5 * * * *')
    ])
])

pipeline {
    agent any
    
    stages {
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

        stage('Execute Selected Action') {
            steps {
                script {
                    switch(params.ACTION) {
                        case 'Infrastructure Bootstrapping':
                            dir("/workspace/aws") {
                                sh 'terraform init'
                                sh 'terraform plan -out=tfplan'
                                sh 'terraform apply -auto-approve tfplan'
                            }
                            break
                        
                        case 'Infrastructure Configuration':
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
                            break
                        
                        case 'Application Deployment':
                            dir("/workspace/ansible") {
                                sh """
                                    ansible-playbook -i aws_ec2.yaml helm-playbook.yaml \
                                        --private-key=/workspace/aws/id_rsa \
                                        -e \"ansible_ssh_common_args='-o StrictHostKeyChecking=no'\"
                                """
                            }
                            break
                        
                        case 'Stop running Server':
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
                            break
                        
                        case 'Start Server':
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
                            break
                        
                        case 'Display Addresses':
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
                            break
                        
                        case 'Destroy Infrastructure':
                            if (params.DESTROY_CONFIRMATION == 'destroy') {
                                dir("/workspace/aws") {
                                    sh 'terraform destroy -auto-approve'
                                }
                            } else {
                                error("❌ Destroy confirmation not provided. Please type 'destroy' in DESTROY_CONFIRMATION to proceed.")
                            }
                            break
                        
                        default:
                            error("⚠️ No valid action selected. Please choose a valid action to proceed.")
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