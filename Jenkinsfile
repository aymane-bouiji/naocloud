pipeline {
    agent any
    
    parameters {
        string(name: 'AWS_REGION', defaultValue: 'eu-west-1', description: 'AWS region to use (e.g., eu-west-1)')
        string(name: 'LOG_LEVEL', defaultValue: 'INFO', description: 'Log detail level: INFO or DEBUG. Defaults to INFO.')
        string(name: 'CLUSTER_VERSION', defaultValue: '23.09', description: 'Cluster version for naocloud image')
        // Hidden property that will be used to rebuild the job with current state
        string(name: 'SERVER_STATE', defaultValue: '', description: 'Hidden property for server state tracking')
    }
    
    stages {
        stage('Check Instance States') {
            steps {
                sh '''
                    set +x
                    export AWS_DEFAULT_REGION=${AWS_REGION}
                    echo "=== Checking Instance States ==="
                    
                    # Query master and worker instances
                    MASTER_DATA=$(aws ec2 describe-instances \
                        --filters "Name=tag:Name,Values=master_instance" \
                        --query "Reservations[].Instances[].[InstanceId,State.Name]" \
                        --output text 2>/tmp/aws_error.log || echo "")
                    WORKER_DATA=$(aws ec2 describe-instances \
                        --filters "Name=tag:Name,Values=worker_instance" \
                        --query "Reservations[].Instances[].[InstanceId,State.Name]" \
                        --output text 2>/tmp/aws_error.log || echo "")
                    
                    # Log any AWS CLI errors
                    if [ -s /tmp/aws_error.log ]; then
                        echo "AWS CLI errors:"
                        cat /tmp/aws_error.log
                    fi
                    
                    # Determine states
                    echo "HAS_INSTANCES=false" > instance_states.properties
                    echo "HAS_RUNNING=false" >> instance_states.properties
                    echo "HAS_STOPPED=false" >> instance_states.properties
                    
                    if [ -n "$MASTER_DATA" ] || [ -n "$WORKER_DATA" ]; then
                        echo "HAS_INSTANCES=true" > instance_states.properties
                        if echo "$MASTER_DATA" | grep -q "running" || echo "$WORKER_DATA" | grep -q "running"; then
                            echo "HAS_RUNNING=true" >> instance_states.properties
                        fi
                        if echo "$MASTER_DATA" | grep -q "stopped" || echo "$WORKER_DATA" | grep -q "stopped"; then
                            echo "HAS_STOPPED=true" >> instance_states.properties
                        fi
                    fi
                    
                    echo "=== Instance States Saved ==="
                '''
                
                script {
                    def props = readProperties file: 'instance_states.properties'
                    env.HAS_INSTANCES = props.HAS_INSTANCES
                    env.HAS_RUNNING = props.HAS_RUNNING
                    env.HAS_STOPPED = props.HAS_STOPPED
                    
                    // Generate available actions based on actual infrastructure state
                    def availableActions = []
                    
                    if (env.HAS_INSTANCES == 'false') {
                        availableActions.add("Infrastructure Bootstrapping")
                    }
                    if (env.HAS_RUNNING == 'true') {
                        availableActions.add("Infrastructure Configuration")
                        availableActions.add("Application Deployment")
                        availableActions.add("Stop running Server")
                        availableActions.add("Display Addresses")
                    }
                    if (env.HAS_STOPPED == 'true') {
                        availableActions.add("Start Server")
                    }
                    if (env.HAS_INSTANCES == 'true') {
                        availableActions.add("Destroy Infrastructure")
                    }
                    
                    // If no actions determined, provide a fallback
                    if (availableActions.isEmpty()) {
                        availableActions.add("No valid actions available")
                    }
                    
                    // Store the current state and available actions
                    env.SERVER_STATE_CURRENT = "has_instances=${env.HAS_INSTANCES},has_running=${env.HAS_RUNNING},has_stopped=${env.HAS_STOPPED}"
                    env.AVAILABLE_ACTIONS = availableActions.join(',')
                    
                    echo "Current server state: ${env.SERVER_STATE_CURRENT}"
                    echo "Available actions: ${env.AVAILABLE_ACTIONS}"
                    
                    // Check if the job was rebuilt with accurate state
                    if (params.SERVER_STATE != env.SERVER_STATE_CURRENT) {
                        // First run or state changed, rebuild with current state
                        echo "Server state changed or first run detected. Rebuilding job with updated state..."
                        
                        // Build the action parameter for the new build
                        def actionParam = '''
                            <input type="choice" name="ACTION" description="Select an action to perform">
                        '''
                        availableActions.each { action ->
                            actionParam += "<option value=\"${action}\">${action}</option>"
                        }
                        actionParam += '</input>'
                        
                        // Add destroy confirmation parameter conditionally
                        def destroyParam = ''
                        if (availableActions.contains("Destroy Infrastructure")) {
                            destroyParam = '''
                                <input type="string" name="DESTROY_CONFIRMATION" default="" 
                                description="Type &quot;destroy&quot; to confirm deletion of the cloud environment (only required for Destroy Infrastructure)">
                                </input>
                            '''
                        }
                        
                        // Rebuild with current parameters plus accurate state
                        build job: env.JOB_NAME, parameters: [
                            string(name: 'AWS_REGION', value: params.AWS_REGION),
                            string(name: 'LOG_LEVEL', value: params.LOG_LEVEL),
                            string(name: 'CLUSTER_VERSION', value: params.CLUSTER_VERSION),
                            string(name: 'SERVER_STATE', value: env.SERVER_STATE_CURRENT),
                            text(name: 'ACTION_CHOICES', value: actionParam),
                            text(name: 'DESTROY_PARAM', value: destroyParam)
                        ], wait: false
                        
                        // Abort the current build
                        currentBuild.result = 'ABORTED'
                        error("Rebuilding job with updated state...")
                    } else {
                        echo "Server state unchanged. Continuing with execution..."
                        
                        // Dynamically create ACTION parameter
                        if (!params.ACTION) {
                            echo "ACTION parameter not set. Please select an action from the available options."
                            currentBuild.result = 'ABORTED'
                            error("ACTION parameter not set")
                        }
                        
                        // Check if the selected action is valid
                        if (!env.AVAILABLE_ACTIONS.split(',').contains(params.ACTION)) {
                            echo "Selected action '${params.ACTION}' is not currently valid. Available actions: ${env.AVAILABLE_ACTIONS}"
                            currentBuild.result = 'ABORTED'
                            error("Invalid action selected")
                        }
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
                
                script {
                    echo "Selected action: ${params.ACTION}"
                    echo "Available actions based on current state:"
                    env.AVAILABLE_ACTIONS.split(',').each { action ->
                        echo "- ${action}"
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
                    if (params.ACTION == 'No valid actions available' || params.ACTION == 'Error: Script execution failed') {
                        error("❌ Invalid action selected: ${params.ACTION}. Check instance states and try again.")
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
                        echo "1. Run again to get updated action options based on new state"
                        break
                    case 'Start Server':
                        echo "✅ Server started successfully! Next steps:"
                        echo "1. Run again to get updated action options based on new state"
                        break
                    case 'Stop running Server':
                        echo "✅ Server stopped successfully! Next steps:"
                        echo "1. Run again to get updated action options based on new state"
                        break
                    case 'Destroy Infrastructure':
                        echo "✅ Infrastructure destroyed successfully! Next steps:"
                        echo "1. Run again to get updated action options based on new state"
                        break
                    default:
                        echo "✅ Action '${params.ACTION}' completed successfully"
                        echo "Run again to get updated actions based on current state"
                }
            }
        }
    }
}