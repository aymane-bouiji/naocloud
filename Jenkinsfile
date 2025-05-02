pipeline {
    agent any
    
    options {
        // This prevents concurrent builds which could affect parameter refresh
        disableConcurrentBuilds()
    }
    
    triggers {
        pollSCM('*/5 * * * *')
    }
    
    parameters {
        // Server Status Parameter - this will be used to display current status
        string(name: 'SERVER_STATUS', defaultValue: 'CHECKING...', description: 'Current server status (auto-populated)')
        
        // Action Selection - primary parameter that controls visibility of others
        choice(name: 'ACTION', choices: ['Select Action', 'Create/Manage Infrastructure', 'Manage Server', 'View Information'], description: 'Select the action you want to perform')
        
        // Infrastructure parameters - shown when ACTION = Create/Manage Infrastructure
        booleanParam(name: 'INFRASTRUCTURE_BOOTSTRAPPING', defaultValue: false, description: 'Set up and create the cloud environment')
        booleanParam(name: 'INFRASTRUCTURE_CONFIGURATION', defaultValue: false, description: 'Configure the cloud setup and manage Application images')
        booleanParam(name: 'APPLICATION_DEPLOYMENT', defaultValue: false, description: 'Deploy applications to the cloud')
        booleanParam(name: 'DESTROY_INFRASTRUCTURE', defaultValue: false, description: 'Delete the entire cloud environment')
        string(name: 'DESTROY_CONFIRMATION', defaultValue: '', description: 'Type "destroy" to confirm deletion of the cloud environment')
        
        // Server management parameters - shown when ACTION = Manage Server
        booleanParam(name: 'STOP_SERVER', defaultValue: false, description: 'Pause (stop) the server')
        booleanParam(name: 'START_SERVER', defaultValue: false, description: 'Start the stopped server')
        
        // Information parameters - shown when ACTION = View Information
        booleanParam(name: 'DISPLAY_ADDRESSES', defaultValue: true, description: 'Display addresses of running server')
        
        // Common parameters - always shown
        string(name: 'AWS_REGION', defaultValue: 'eu-west-1', description: 'AWS region to use (e.g., eu-west-1)')
        string(name: 'LOG_LEVEL', defaultValue: 'INFO', description: 'Log detail level: INFO or DEBUG. Defaults to INFO.')
        string(name: 'CLUSTER_VERSION', defaultValue: '23.09', description: 'Cluster version for naocloud image')
    }
    
    stages {
        stage('Check Server State') {
            steps {
                script {
                    // Store server state for later use
                    env.SERVER_STATE = sh(script: '''
                        export AWS_DEFAULT_REGION=${AWS_REGION}
                        MASTER_COUNT=$(aws ec2 describe-instances \
                            --filters "Name=tag:Name,Values=master_instance" \
                            --query "length(Reservations[].Instances[])" \
                            --output text)
                            
                        if [ "$MASTER_COUNT" -eq "0" ]; then
                            echo "NOT_CREATED"
                        else
                            RUNNING_COUNT=$(aws ec2 describe-instances \
                                --filters "Name=tag:Name,Values=master_instance,worker_instance" "Name=instance-state-name,Values=running" \
                                --query "length(Reservations[].Instances[])" \
                                --output text)
                            INSTANCE_COUNT=$(aws ec2 describe-instances \
                                --filters "Name=tag:Name,Values=master_instance,worker_instance" \
                                --query "length(Reservations[].Instances[])" \
                                --output text)
                                
                            if [ "$RUNNING_COUNT" -eq "$INSTANCE_COUNT" ]; then
                                echo "RUNNING"
                            elif [ "$RUNNING_COUNT" -eq "0" ]; then
                                echo "STOPPED"
                            else
                                echo "PARTIAL"
                            fi
                        fi
                    ''', returnStdout: true).trim()
                    
                    echo "Current server state: ${env.SERVER_STATE}"
                    
                    // Update the status parameter for the next build
                    properties([
                        parameters([
                            string(name: 'SERVER_STATUS', defaultValue: env.SERVER_STATE, description: 'Current server status')
                        ])
                    ])
                }
            }
        }

        stage('Parameter Validation') {
            steps {
                script {
                    // Only perform checks for relevant parameters based on action
                    if (params.ACTION == 'Create/Manage Infrastructure') {
                        // Check destroy confirmation
                        if (params.DESTROY_INFRASTRUCTURE && params.DESTROY_CONFIRMATION != 'destroy') {
                            error("❌ Destroy confirmation not provided. Please type 'destroy' in DESTROY_CONFIRMATION to proceed.")
                        }
                        
                        // Check contradictory actions
                        if (params.INFRASTRUCTURE_BOOTSTRAPPING && params.DESTROY_INFRASTRUCTURE) {
                            error("❌ You cannot bootstrap and destroy infrastructure at the same time. Please select only one.")
                        }
                        
                        // Check if any infrastructure action is selected
                        if (!params.INFRASTRUCTURE_BOOTSTRAPPING && 
                            !params.INFRASTRUCTURE_CONFIGURATION && 
                            !params.APPLICATION_DEPLOYMENT && 
                            !params.DESTROY_INFRASTRUCTURE) {
                            error("⚠️ No infrastructure action selected. Please choose at least one operation.")
                        }
                    }
                    
                    if (params.ACTION == 'Manage Server') {
                        // Check contradictory server actions
                        if (params.START_SERVER && params.STOP_SERVER) {
                            error("❌ You cannot start and stop the server at the same time. Please select only one.")
                        }
                        
                        // Check if any server action is selected
                        if (!params.START_SERVER && !params.STOP_SERVER) {
                            error("⚠️ No server action selected. Please choose at least one operation.")
                        }
                        
                        // Verify server exists before attempting to manage it
                        if (env.SERVER_STATE == "NOT_CREATED") {
                            error("❌ Cannot manage server because it doesn't exist yet. Please create infrastructure first.")
                        }
                    }
                    
                    if (params.ACTION == 'View Information') {
                        // Nothing to validate here
                    }
                    
                    if (params.ACTION == 'Select Action') {
                        error("⚠️ Please select an action from the dropdown menu.")
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

        // Infrastructure stages - only run when ACTION = Create/Manage Infrastructure
        stage('Infrastructure Bootstrapping') {
            when {
                allOf {
                    expression { params.ACTION == 'Create/Manage Infrastructure' }
                    expression { params.INFRASTRUCTURE_BOOTSTRAPPING }
                }
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
                allOf {
                    expression { params.ACTION == 'Create/Manage Infrastructure' }
                    expression { params.INFRASTRUCTURE_CONFIGURATION }
                }
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
                allOf {
                    expression { params.ACTION == 'Create/Manage Infrastructure' }
                    expression { params.APPLICATION_DEPLOYMENT }
                }
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
        
        stage('Destroy Infrastructure') {
            when {
                allOf {
                    expression { params.ACTION == 'Create/Manage Infrastructure' }
                    expression { params.DESTROY_INFRASTRUCTURE }
                    expression { params.DESTROY_CONFIRMATION == 'destroy' }
                }
            }
            steps {
                dir("/workspace/aws") {
                    sh 'terraform destroy -auto-approve'
                }
            }
        }

        // Server management stages - only run when ACTION = Manage Server
        stage('Stop Server') {
            when {
                allOf {
                    expression { params.ACTION == 'Manage Server' }
                    expression { params.STOP_SERVER }
                }
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
                allOf {
                    expression { params.ACTION == 'Manage Server' }
                    expression { params.START_SERVER }
                }
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

        // Information stages - only run when ACTION = View Information
        stage('Display addresses of the server') {
            when {
                allOf {
                    expression { params.ACTION == 'View Information' }
                    expression { params.DISPLAY_ADDRESSES }
                }
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