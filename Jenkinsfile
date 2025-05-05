pipeline {
    agent any
    
    parameters {
        string(name: 'AWS_REGION', defaultValue: 'eu-west-1', description: 'AWS region to use (e.g., eu-west-1)')
        string(name: 'LOG_LEVEL', defaultValue: 'INFO', description: 'Log detail level: INFO or DEBUG. Defaults to INFO.')
        string(name: 'CLUSTER_VERSION', defaultValue: '23.09', description: 'Cluster version for naocloud image')
        activeChoice(
            name: 'ACTION',
            description: 'Select an action to perform',
            filterable: false,
            choiceType: 'PT_SINGLE_SELECT',
            script: [
                $class: 'GroovyScript',
                script: [
                    classpath: [],
                    sandbox: true,
                    script: '''
                        // Execute shell script to determine available actions based on server state
                        def actions = sh(script: """
                            set +x
                            export AWS_DEFAULT_REGION=${AWS_REGION}
                            
                            # Check master instances
                            MASTER_DATA=\$(aws ec2 describe-instances \
                                --filters "Name=tag:Name,Values=master_instance" \
                                --query "Reservations[].Instances[].State.Name" \
                                --output text 2>/dev/null)
                            
                            # Check worker instances
                            WORKER_DATA=\$(aws ec2 describe-instances \
                                --filters "Name=tag:Name,Values=worker_instance" \
                                --query "Reservations[].Instances[].State.Name" \
                                --output text 2>/dev/null)
                            
                            # Initialize state flags
                            has_instances=false
                            has_running=false
                            has_stopped=false
                            
                            # Process master and worker data
                            for state in \$MASTER_DATA \$WORKER_DATA; do
                                has_instances=true
                                if [ "\$state" = "running" ] || [ "\$state" = "pending" ]; then
                                    has_running=true
                                elif [ "\$state" = "stopped" ]; then
                                    has_stopped=true
                                fi
                            done
                            
                            # Determine available actions based on state
                            actions=()
                            if [ "\$has_instances" = "false" ]; then
                                actions+=("Infrastructure Bootstrapping")
                            fi
                            if [ "\$has_running" = "true" ]; then
                                actions+=("Infrastructure Configuration")
                                actions+=("Application Deployment")
                                actions+=("Stop running Server")
                                actions+=("Display Addresses")
                            fi
                            if [ "\$has_stopped" = "true" ]; then
                                actions+=("Start Server")
                            fi
                            if [ "\$has_instances" = "true" ]; then
                                actions+=("Destroy Infrastructure")
                            fi
                            
                            # Output actions, one per line
                            for action in "\${actions[@]}"; do
                                echo "\$action"
                            done
                        """, returnStdout: true).trim().split('\n')
                        
                        // Return the list of actions for the Active Choices parameter
                        return actions ?: ['No actions available']
                    '''
                ]
            ]
        )
        string(
            name: 'DESTROY_CONFIRMATION',
            defaultValue: '',
            description: 'Type "destroy" to confirm deletion of the cloud environment (only required for Destroy Infrastructure)'
        )
    }
    
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
                    
                    # Capture server state information for later use
                    echo "Setting environment variables based on server state..."
                    
                    # Determine if there are any instances
                    if [ -n "$MASTER_DATA" ] || [ -n "$WORKER_DATA" ]; then
                        echo "HAS_INSTANCES=true" > server_state.properties
                    else
                        echo "HAS_INSTANCES=false" > server_state.properties
                    fi
                    
                    # Determine if there are running instances
                    if echo "$MASTER_DATA" | grep -q "running" || echo "$WORKER_DATA" | grep -q "running"; then
                        echo "HAS_RUNNING=true" >> server_state.properties
                    else
                        echo "HAS_RUNNING=false" >> server_state.properties
                    fi
                    
                    # Determine if there are stopped instances
                    if echo "$MASTER_DATA" | grep -q "stopped" || echo "$WORKER_DATA" | grep -q "stopped"; then
                        echo "HAS_STOPPED=true" >> server_state.properties
                    else
                        echo "HAS_STOPPED=false" >> server_state.properties
                    fi
                    
                    echo "==================="
                '''
                
                script {
                    def props = readProperties file: 'server_state.properties'
                    env.HAS_INSTANCES = props.HAS_INSTANCES
                    env.HAS_RUNNING = props.HAS_RUNNING
                    env.HAS_STOPPED = props.HAS_STOPPED
                    
                    echo "Recommended actions based on current state:"
                    if (env.HAS_INSTANCES == 'false') {
                        echo "- Infrastructure Bootstrapping (create new infrastructure)"
                    }
                    if (env.HAS_RUNNING == 'true') {
                        echo "- Infrastructure Configuration (configure existing infrastructure)"
                        echo "- Application Deployment (deploy applications)"
                        echo "- Stop running Server (stop instances)"
                        echo "- Display Addresses (show public addresses)"
                    }
                    if (env.HAS_STOPPED == 'true') {
                        echo "- Start Server (restart instances)"
                    }
                    if (env.HAS_INSTANCES == 'true') {
                        echo "- Destroy Infrastructure (remove all resources)"
                    }
                }
            }
        }

        stage('Parameter Validation') {
            steps {
                script {
                    if (params.ACTION == 'Infrastructure Bootstrapping' && env.HAS_INSTANCES == 'true') {
                        error("❌ Cannot bootstrap infrastructure when instances already exist. Consider using Destroy Infrastructure first.")
                    }
                    if (params.ACTION == 'Start Server' && env.HAS_STOPPED != 'true') {
                        error("❌ No stopped instances found to start.")
                    }
                    if (params.ACTION == 'Stop running Server' && env.HAS_RUNNING != 'true') {
                        error("❌ No running instances found to stop.")
                    }
                    if ((params.ACTION == 'Infrastructure Configuration' || 
                         params.ACTION == 'Application Deployment' || 
                         params.ACTION == 'Display Addresses') && 
                        env.HAS_RUNNING != 'true') {
                        error("❌ This action requires running instances. Please start the server first.")
                    }
                    if (params.ACTION == 'Destroy Infrastructure' && params.DESTROY_CONFIRMATION != 'destroy') {
                        error("❌ Destroy confirmation not provided. Please type 'destroy' in DESTROY_CONFIRMATION to proceed.")
                    }
                }
            }
        }

        stage('Execute Action') {
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
        success {
            echo "Pipeline completed successfully"
            script {
                switch(params.ACTION) {
                    case 'Infrastructure Bootstrapping':
                        echo "✅ Infrastructure created successfully! Next steps:"
                        echo "1. Run again with ACTION = 'Infrastructure Configuration'"
                        echo "2. Then run with ACTION = 'Application Deployment'"
                        break
                    case 'Start Server':
                        echo "✅ Server started successfully! Next steps:"
                        echo "1. Run with ACTION = 'Infrastructure Configuration' if needed"
                        echo "2. Run with ACTION = 'Application Deployment' if needed"
                        echo "3. View server addresses with ACTION = 'Display Addresses'"
                        break
                }
            }
        }
    }
}