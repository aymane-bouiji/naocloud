pipeline {
    agent any
    
    triggers {
        pollSCM('*/5 * * * *')
    }
    
    // Define base parameters
    parameters {
        string(name: 'AWS_REGION', defaultValue: 'eu-west-1', description: 'AWS region to use (e.g., eu-west-1)')
        string(name: 'LOG_LEVEL', defaultValue: 'INFO', description: 'Log detail level: INFO or DEBUG. Defaults to INFO.')
        string(name: 'CLUSTER_VERSION', defaultValue: '23.09', description: 'Cluster version for naocloud image')
    }
    
    stages {
        stage('Active Parameter Detection') {
            steps {
                script {
                    // Set AWS region for commands
                    env.AWS_DEFAULT_REGION = params.AWS_REGION
                    
                    echo "Detecting infrastructure state..."
                    
                    // Check for existing infrastructure by looking for terraform state
                    def terraformStateExists = false
                    try {
                        dir("/workspace/aws") {
                            terraformStateExists = sh(
                                script: 'test -d .terraform && echo "true" || echo "false"', 
                                returnStdout: true
                            ).trim() == "true" || fileExists('/workspace/aws/terraform.tfstate')
                        }
                    } catch (Exception e) {
                        echo "Error checking terraform state: ${e.message}"
                    }
                    
                    // Determine if instances exist and their state
                    def instancesRunning = false
                    def instancesStopped = false
                    
                    try {
                        // Check for running instances
                        def runningInstances = sh(
                            script: '''
                                aws ec2 describe-instances \
                                --filters "Name=tag:Name,Values=master_instance,worker_instance" \
                                "Name=instance-state-name,Values=running,pending" \
                                --query "length(Reservations[].Instances[])" \
                                --output text || echo "0"
                            ''',
                            returnStdout: true
                        ).trim()
                        
                        // Check for stopped instances
                        def stoppedInstances = sh(
                            script: '''
                                aws ec2 describe-instances \
                                --filters "Name=tag:Name,Values=master_instance,worker_instance" \
                                "Name=instance-state-name,Values=stopped" \
                                --query "length(Reservations[].Instances[])" \
                                --output text || echo "0"
                            ''',
                            returnStdout: true
                        ).trim()
                        
                        // Set state flags
                        instancesRunning = runningInstances != '0' && runningInstances != ''
                        instancesStopped = stoppedInstances != '0' && stoppedInstances != ''
                        
                        echo "Infrastructure state detected:"
                        echo "- Terraform state exists: ${terraformStateExists}"
                        echo "- Running instances: ${instancesRunning}"
                        echo "- Stopped instances: ${instancesStopped}"
                    } catch (Exception e) {
                        echo "Error detecting instance state: ${e.message}"
                        // If we can't determine state, assume nothing exists
                        instancesRunning = false
                        instancesStopped = false
                    }
                    
                    // Generate the action choices HTML
                    def actionChoices = []
                    
                    // Always available
                    if (!terraformStateExists) {
                        actionChoices.add("Infrastructure Bootstrapping")
                    } else {
                        // If infrastructure exists
                        actionChoices.add("Infrastructure Configuration")
                        actionChoices.add("Application Deployment")
                        actionChoices.add("Destroy Infrastructure")
                        
                        // Instance specific actions
                        if (instancesRunning) {
                            actionChoices.add("Stop running Server")
                            actionChoices.add("Display Addresses")
                        }
                        
                        if (instancesStopped) {
                            actionChoices.add("Start Server")
                        }
                    }
                    
                    // Store the current state in environment variables
                    env.TERRAFORM_STATE_EXISTS = terraformStateExists.toString()
                    env.INSTANCES_RUNNING = instancesRunning.toString()
                    env.INSTANCES_STOPPED = instancesStopped.toString()
                    
                    // Create the Active Choice Parameter script that will be used for the ACTION parameter
                    def activeChoiceScript = """
                        import hudson.model.*;
                        import jenkins.model.*;
                        
                        def actions = []
                        
                        boolean terraformStateExists = Boolean.parseBoolean(System.getenv('TERRAFORM_STATE_EXISTS') ?: 'false')
                        boolean instancesRunning = Boolean.parseBoolean(System.getenv('INSTANCES_RUNNING') ?: 'false')
                        boolean instancesStopped = Boolean.parseBoolean(System.getenv('INSTANCES_STOPPED') ?: 'false')
                        
                        // Build choices based on infrastructure state
                        if (!terraformStateExists) {
                            actions.add("Infrastructure Bootstrapping")
                        } else {
                            actions.add("Infrastructure Configuration")
                            actions.add("Application Deployment")
                            actions.add("Destroy Infrastructure")
                            
                            if (instancesRunning) {
                                actions.add("Stop running Server")
                                actions.add("Display Addresses")
                            }
                            
                            if (instancesStopped) {
                                actions.add("Start Server")
                            }
                        }
                        
                        return actions
                    """
                    
                    // Create a Groovy script file for the Active Choice Parameter
                    writeFile file: 'actionChoices.groovy', text: activeChoiceScript
                    
                    // Create a file with required properties to help the UI
                    def stateProperties = """
                        terraformStateExists=${terraformStateExists}
                        instancesRunning=${instancesRunning}
                        instancesStopped=${instancesStopped}
                        availableActions=${actionChoices.join(',')}
                    """
                    writeFile file: 'infrastructure_state.properties', text: stateProperties
                    
                    // Create HTML output to show available actions
                    def htmlContent = """
                        <h3>Available Actions Based on Current Infrastructure State:</h3>
                        <ul>
                    """
                    
                    actionChoices.each { action ->
                        htmlContent += "<li>${action}</li>"
                    }
                    
                    htmlContent += """
                        </ul>
                        <p><b>Please select an action from the dropdown below:</b></p>
                    """
                    
                    // Display the HTML content
                    echo htmlContent
                    
                    // Save the action choices for later usage
                    env.ACTION_CHOICES = actionChoices.join(',')
                    
                    // Create prompt for ACTION using a dynamically generated dropdown
                    def actionOptions = []
                    actionChoices.each { choice ->
                        actionOptions.add("<option value=\"${choice}\">${choice}</option>")
                    }
                    
                    def actionDropdown = """
                        <select name="ACTION" id="action-select">
                            ${actionOptions.join('')}
                        </select>
                    """
                    
                    // Create the destroy confirmation input if needed
                    def destroyConfirmation = ""
                    if (actionChoices.contains("Destroy Infrastructure")) {
                        destroyConfirmation = """
                            <div id="destroy-confirmation" style="display:none; margin-top: 10px;">
                                <label for="destroy-input">Type "destroy" to confirm deletion:</label>
                                <input type="text" id="destroy-input" name="DESTROY_CONFIRMATION">
                            </div>
                            <script>
                                document.getElementById('action-select').addEventListener('change', function() {
                                    var destroyConfirmation = document.getElementById('destroy-confirmation');
                                    if (this.value === 'Destroy Infrastructure') {
                                        destroyConfirmation.style.display = 'block';
                                    } else {
                                        destroyConfirmation.style.display = 'none';
                                    }
                                });
                            </script>
                        """
                    }
                    
                    // Build a custom input form
                    def customForm = """
                        <div style="background-color: #f5f5f5; padding: 15px; border-radius: 5px; margin-bottom: 20px;">
                            <h2>Cloud Infrastructure Management</h2>
                            
                            <div style="margin-bottom: 10px;">
                                <label for="action-select"><b>Select an action:</b></label><br>
                                ${actionDropdown}
                            </div>
                            
                            ${destroyConfirmation}
                            
                            <div style="margin-top: 20px;">
                                <input type="submit" value="Execute Action" style="background-color: #0066cc; color: white; padding: 8px 15px; border: none; border-radius: 4px; cursor: pointer;">
                            </div>
                        </div>
                    """
                    
                    // In a real Jenkins environment, this would use the input step
                    // For now, just display the form and simulate user input
                    
                    // Check if the ACTION parameter is set
                    if (!params.containsKey('ACTION') || params.ACTION == null || params.ACTION.trim() == '') {
                        // If no ACTION is specified, show the form and wait for user input
                        def result = input(
                            id: 'userInput',
                            message: 'Select an action to perform',
                            parameters: [
                                choice(name: 'ACTION', choices: actionChoices, description: 'Select an action to perform on the cloud infrastructure')
                            ]
                        )
                        
                        // Store the ACTION for use in subsequent stages
                        env.ACTION = result.ACTION
                    } else {
                        // Use the provided ACTION parameter
                        env.ACTION = params.ACTION
                    }
                    
                    // Validate if the action is still valid in the current state
                    if (!actionChoices.contains(env.ACTION)) {
                        error("Selected action '${env.ACTION}' is not valid in the current infrastructure state. Please select from: ${actionChoices.join(', ')}")
                    }
                    
                    echo "Proceeding with action: ${env.ACTION}"
                }
            }
        }
        
        stage('Parameter Validation') {
            steps {
                script {
                    // Validate destroy confirmation
                    if (env.ACTION == "Destroy Infrastructure") {
                        // Get the destroy confirmation if not provided
                        if (!params.containsKey('DESTROY_CONFIRMATION') || params.DESTROY_CONFIRMATION != 'destroy') {
                            def destroyConfirm = input(
                                id: 'destroyConfirm',
                                message: 'Type "destroy" to confirm deletion of the cloud environment',
                                parameters: [
                                    string(name: 'DESTROY_CONFIRMATION', defaultValue: '', description: 'Type "destroy" to confirm deletion of the cloud environment')
                                ]
                            )
                            
                            if (destroyConfirm.DESTROY_CONFIRMATION != 'destroy') {
                                error("❌ Destroy confirmation not provided. You must type 'destroy' to proceed with infrastructure deletion.")
                            }
                        }
                    }
                }
            }
        }

        stage('Infrastructure Bootstrapping') {
            when {
                expression { env.ACTION == "Infrastructure Bootstrapping" }
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
                expression { env.ACTION == "Infrastructure Configuration" }
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
                expression { env.ACTION == "Application Deployment" }
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
                expression { env.ACTION == "Stop running Server" }
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
                expression { env.ACTION == "Start Server" }
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
                expression { env.ACTION == "Destroy Infrastructure" }
            }
            steps {
                dir("/workspace/aws") {
                    sh 'terraform destroy -auto-approve'
                }
            }
        }
        
        stage('Display addresses of the server') {
            when {
                expression { env.ACTION == "Display Addresses" }
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