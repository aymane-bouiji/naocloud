pipeline {
    agent any
    
    // Need to manually update these choices when infrastructure changes
    // The first job will display the valid choices in its output
    parameters {
        string(name: 'AWS_REGION', defaultValue: 'eu-west-1', description: 'AWS region to use (e.g., eu-west-1)')
        string(name: 'LOG_LEVEL', defaultValue: 'INFO', description: 'Log detail level: INFO or DEBUG. Defaults to INFO.')
        string(name: 'CLUSTER_VERSION', defaultValue: '23.09', description: 'Cluster version for naocloud image')
        choice(name: 'ACTION', choices: [
            'Infrastructure Bootstrapping',
            'Infrastructure Configuration', 
            'Application Deployment',
            'Destroy Infrastructure',
            'Stop running Server',
            'Start Server',
            'Display Addresses'
        ], description: 'Action to perform - must match one of the available actions from the detection job')
    }
    
    stages {
        stage('Load Infrastructure State') {
            steps {
                script {
                    // Set AWS region for commands
                    env.AWS_DEFAULT_REGION = params.AWS_REGION
                    
                    // Create directory for state files
                    sh "mkdir -p ${WORKSPACE}/state"
                    
                    // Copy the state files from the previous job
                    copyArtifacts(
                        projectName: 'Detect-Infrastructure-State',
                        filter: 'state/*',
                        target: '.',
                        flatten: false,
                        selector: lastSuccessful()
                    )
                    
                    // Load state from files
                    if (fileExists("${WORKSPACE}/state/infrastructure_state.txt")) {
                        def stateFile = readFile "${WORKSPACE}/state/infrastructure_state.txt"
                        def stateLines = stateFile.split('\n')
                        
                        stateLines.each { line ->
                            if (line.contains('=')) {
                                def parts = line.split('=', 2)
                                env[parts[0]] = parts[1]
                            }
                        }
                        
                        echo "Loaded infrastructure state:"
                        echo "- Terraform state exists: ${env.TERRAFORM_STATE_EXISTS}"
                        echo "- Running instances: ${env.INSTANCES_RUNNING}"
                        echo "- Stopped instances: ${env.INSTANCES_STOPPED}"
                    } else {
                        error "Infrastructure state file not found. Please run the 'Detect Infrastructure State' job first."
                    }
                    
                    // Load and validate available actions
                    def availableActions = []
                    if (fileExists("${WORKSPACE}/state/available_actions.txt")) {
                        availableActions = readFile("${WORKSPACE}/state/available_actions.txt").split('\n')
                        
                        echo "Available actions:"
                        availableActions.each { action ->
                            echo "- ${action}"
                        }
                        
                        // Validate that the selected action is available
                        if (!availableActions.contains(params.ACTION)) {
                            error """Invalid action selected: ${params.ACTION}
                            
Available actions based on current infrastructure state are:
${availableActions.join('\n')}

Please select one of these actions and run the job again."""
                        }
                        
                        echo "Selected action '${params.ACTION}' is valid for the current infrastructure state."
                    } else {
                        error "Available actions file not found. Please run the 'Detect Infrastructure State' job first."
                    }
                }
            }
        }
        
        stage('Validate Destroy Confirmation') {
            when {
                expression { params.ACTION == "Destroy Infrastructure" }
            }
            steps {
                script {
                    def destroyConfirm = input(
                        id: 'destroyConfirm',
                        message: 'Type "destroy" to confirm deletion of the cloud environment',
                        parameters: [
                            string(name: 'DESTROY_CONFIRMATION', defaultValue: '', 
                                   description: 'Type "destroy" to confirm deletion of the cloud environment')
                        ]
                    )
                    
                    if (destroyConfirm.DESTROY_CONFIRMATION != 'destroy') {
                        error "❌ Destroy confirmation not provided. You must type 'destroy' to proceed with infrastructure deletion."
                    }
                }
            }
        }
        
        stage('Infrastructure Bootstrapping') {
            when {
                expression { params.ACTION == "Infrastructure Bootstrapping" }
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
                expression { params.ACTION == "Infrastructure Configuration" }
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
                expression { params.ACTION == "Application Deployment" }
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
                expression { params.ACTION == "Stop running Server" }
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
                expression { params.ACTION == "Start Server" }
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
                expression { params.ACTION == "Destroy Infrastructure" }
            }
            steps {
                dir("/workspace/aws") {
                    sh 'terraform destroy -auto-approve'
                }
            }
        }
        
        stage('Display addresses of the server') {
            when {
                expression { params.ACTION == "Display Addresses" }
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
        success {
            echo "Action '${params.ACTION}' completed successfully."
            build job: 'Detect-Infrastructure-State', parameters: [
                string(name: 'AWS_REGION', value: params.AWS_REGION)
            ], wait: false
        }
        failure {
            echo "Action '${params.ACTION}' failed."
        }
        always {
            cleanWs()
        }
    }
}