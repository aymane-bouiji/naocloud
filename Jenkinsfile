pipeline {
    agent any
    
    parameters {
        string(name: 'AWS_REGION', defaultValue: 'eu-west-1', description: 'AWS region to use (e.g., eu-west-1)')
        string(name: 'LOG_LEVEL', defaultValue: 'INFO', description: 'Log detail level: INFO or DEBUG. Defaults to INFO.')
        string(name: 'CLUSTER_VERSION', defaultValue: '23.09', description: 'Cluster version for naocloud image')
        // Use an active choice parameter that looks up the server state each time
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
                        try {
                            def awsRegion = binding.hasVariable('AWS_REGION') ? binding.variables.get('AWS_REGION') : 'eu-west-1'
                            
                            // Variables to track instance state
                            def has_instances = false
                            def has_running = false
                            def has_stopped = false
                            
                            // Run AWS CLI command to check instance states
                            def checkCmd = """
                                export AWS_DEFAULT_REGION=${awsRegion}
                                aws ec2 describe-instances --filters "Name=tag:Name,Values=master_instance,worker_instance" --query "Reservations[].Instances[].[Tags[?Key=='Name'].Value | [0], State.Name]" --output text
                            """
                            
                            def result = sh(script: checkCmd, returnStdout: true).trim()
                            
                            // Parse results to determine state
                            if (result) {
                                has_instances = true
                                result.split("\\n").each { line ->
                                    def parts = line.split()
                                    if (parts.length > 1) {
                                        def state = parts[1]
                                        if (state == "running") {
                                            has_running = true
                                        } else if (state == "stopped") {
                                            has_stopped = true
                                        }
                                    }
                                }
                            }
                            
                            // Generate available actions based on state
                            def actions = []
                            
                            if (!has_instances) {
                                actions.add("Infrastructure Bootstrapping")
                            }
                            if (has_running) {
                                actions.add("Infrastructure Configuration")
                                actions.add("Application Deployment")
                                actions.add("Stop running Server")
                                actions.add("Display Addresses")
                            }
                            if (has_stopped) {
                                actions.add("Start Server")
                            }
                            if (has_instances) {
                                actions.add("Destroy Infrastructure")
                            }
                            
                            // If no actions determined, provide a fallback
                            if (actions.isEmpty()) {
                                actions.add("No valid actions available")
                            }
                            
                            return actions
                            
                        } catch (Exception e) {
                            // Log error and provide fallback options
                            def errorMessage = "Error determining server state: ${e.message}"
                            println(errorMessage)
                            
                            // Write to a log file for debugging
                            try {
                                new File('/var/jenkins_home/workspace/naoserver/active_choice_error.log').text = errorMessage
                            } catch (Exception ignored) {}
                            
                            // Return all possible options as fallback
                            return [
                                "Infrastructure Bootstrapping",
                                "Infrastructure Configuration",
                                "Application Deployment",
                                "Stop running Server",
                                "Display Addresses",
                                "Start Server",
                                "Destroy Infrastructure"
                            ]
                        }
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
                    
                    echo "Current server state:"
                    echo "- Has instances: ${env.HAS_INSTANCES}"
                    echo "- Has running instances: ${env.HAS_RUNNING}"
                    echo "- Has stopped instances: ${env.HAS_STOPPED}"
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
                    
                    // Determine valid actions
                    def validActions = []
                    if (env.HAS_INSTANCES != 'true') {
                        validActions.add("Infrastructure Bootstrapping")
                    }
                    if (env.HAS_RUNNING == 'true') {
                        validActions.add("Infrastructure Configuration")
                        validActions.add("Application Deployment")
                        validActions.add("Stop running Server")
                        validActions.add("Display Addresses")
                    }
                    if (env.HAS_STOPPED == 'true') {
                        validActions.add("Start Server")
                    }
                    if (env.HAS_INSTANCES == 'true') {
                        validActions.add("Destroy Infrastructure")
                    }
                    
                    echo "Valid actions for current state:"
                    validActions.each { action ->
                        echo "- ${action}"
                    }
                    
                    // Check if the selected action is valid
                    if (!validActions.contains(params.ACTION)) {
                        error("❌ The selected action '${params.ACTION}' is not valid for the current server state. Please select one of: ${validActions.join(', ')}")
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
                        echo "✅ Infrastructure created successfully!"
                        echo "Run the pipeline again to see updated action options"
                        break
                    case 'Start Server':
                        echo "✅ Server started successfully!"
                        echo "Run the pipeline again to see updated action options"
                        break
                    case 'Stop running Server':
                        echo "✅ Server stopped successfully!"
                        echo "Run the pipeline again to see updated action options"
                        break
                    case 'Destroy Infrastructure':
                        echo "✅ Infrastructure destroyed successfully!"
                        echo "Run the pipeline again to see updated action options"
                        break
                    default:
                        echo "✅ Action '${params.ACTION}' completed successfully"
                        echo "Run the pipeline again to see updated action options"
                }
            }
        }
    }
}