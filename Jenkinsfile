// This requires the Active Choices Plugin to be installed in Jenkins
// Create a new Pipeline job and use this Jenkinsfile

properties([
    // Prevent concurrent builds
    disableConcurrentBuilds(),
    
    // Configure parameters with Active Choices Plugin
    parameters([
        // Primary action selection parameter
        activeChoice(
            name: 'ACTION',
            description: 'Select the action you want to perform',
            script: 
                '''
                return ['Select Action', 'Create/Manage Infrastructure', 'Manage Server', 'View Information']
                ''',
            choiceType: 'SINGLE_SELECT'
        ),
        
        // Server status parameter - automatically updated
        activeChoice(
            name: 'SERVER_STATUS',
            description: 'Current server status (auto-populated)',
            script: 
                '''
                // Get the current server status
                def proc = "aws ec2 describe-instances --filters Name=tag:Name,Values=master_instance,worker_instance --query Reservations[].Instances[].State.Name --output text".execute()
                proc.waitFor()
                
                def output = proc.text.trim()
                if (!output) {
                  return "NOT_CREATED"
                } else if (output.contains("running") && !output.contains("stopped")) {
                  return "RUNNING"
                } else if (output.contains("stopped") && !output.contains("running")) {
                  return "STOPPED"
                } else {
                  return "PARTIAL"
                }
                ''',
            choiceType: 'SINGLE_SELECT',
            referencedParameters: 'AWS_REGION'
        ),
        
        // Dynamic infrastructure options based on ACTION
        activeChoiceReactiveReference(
            name: 'INFRASTRUCTURE_CONTROLS',
            description: 'Infrastructure Options',
            script:
                '''
                if (ACTION == 'Create/Manage Infrastructure') {
                  return """
                    <div style="background-color: #f0f0f0; padding: 10px; border-radius: 5px; margin-bottom: 10px;">
                      <h3>Infrastructure Options</h3>
                      <label style="display: block; margin-bottom: 5px;">
                        <input type="checkbox" name="INFRASTRUCTURE_BOOTSTRAPPING"> Set up and create the cloud environment
                      </label>
                      <label style="display: block; margin-bottom: 5px;">
                        <input type="checkbox" name="INFRASTRUCTURE_CONFIGURATION"> Configure the cloud setup and manage Application images
                      </label>
                      <label style="display: block; margin-bottom: 5px;">
                        <input type="checkbox" name="APPLICATION_DEPLOYMENT"> Deploy applications to the cloud
                      </label>
                      <label style="display: block; margin-bottom: 5px;">
                        <input type="checkbox" name="DESTROY_INFRASTRUCTURE"> Delete the entire cloud environment
                      </label>
                      <div id="destroy-confirmation" style="margin-top: 5px; display: none;">
                        <label>Type "destroy" to confirm deletion:
                          <input type="text" name="DESTROY_CONFIRMATION">
                        </label>
                      </div>
                    </div>
                    <script>
                      document.querySelector('input[name="DESTROY_INFRASTRUCTURE"]').addEventListener('change', function() {
                        document.getElementById('destroy-confirmation').style.display = this.checked ? 'block' : 'none';
                      });
                    </script>
                  """
                } else {
                  return ""
                }
                ''',
            referencedParameters: 'ACTION',
            choiceType: 'FORMATTED_HTML'
        ),
        
        // Dynamic server options based on ACTION and SERVER_STATUS
        activeChoiceReactiveReference(
            name: 'SERVER_CONTROLS',
            description: 'Server Controls',
            script:
                '''
                if (ACTION == 'Manage Server') {
                  def disabled = (SERVER_STATUS == "NOT_CREATED") ? "disabled" : ""
                  def startDisabled = (SERVER_STATUS == "RUNNING") ? "disabled" : ""
                  def stopDisabled = (SERVER_STATUS == "STOPPED") ? "disabled" : ""
                  
                  return """
                    <div style="background-color: #f0f0f0; padding: 10px; border-radius: 5px; margin-bottom: 10px;">
                      <h3>Server Controls</h3>
                      <p>Current Status: <strong>${SERVER_STATUS}</strong></p>
                      <label style="display: block; margin-bottom: 5px;">
                        <input type="checkbox" name="START_SERVER" ${startDisabled} ${disabled}> Start the stopped server
                      </label>
                      <label style="display: block; margin-bottom: 5px;">
                        <input type="checkbox" name="STOP_SERVER" ${stopDisabled} ${disabled}> Pause (stop) the server
                      </label>
                      ${SERVER_STATUS == "NOT_CREATED" ? "<p style='color: red;'>You must create infrastructure first.</p>" : ""}
                    </div>
                  """
                } else {
                  return ""
                }
                ''',
            referencedParameters: 'ACTION, SERVER_STATUS',
            choiceType: 'FORMATTED_HTML'
        ),
        
        // Dynamic information options based on ACTION and SERVER_STATUS
        activeChoiceReactiveReference(
            name: 'INFO_CONTROLS',
            description: 'Information Options',
            script:
                '''
                if (ACTION == 'View Information') {
                  def disabled = (SERVER_STATUS == "NOT_CREATED" || SERVER_STATUS == "STOPPED") ? "disabled" : ""
                  
                  return """
                    <div style="background-color: #f0f0f0; padding: 10px; border-radius: 5px; margin-bottom: 10px;">
                      <h3>Information Options</h3>
                      <label style="display: block; margin-bottom: 5px;">
                        <input type="checkbox" name="DISPLAY_ADDRESSES" checked ${disabled}> Show public addresses of running servers
                      </label>
                      ${(SERVER_STATUS == "NOT_CREATED" || SERVER_STATUS == "STOPPED") ? "<p style='color: orange;'>Server must be running to view addresses.</p>" : ""}
                    </div>
                  """
                } else {
                  return ""
                }
                ''',
            referencedParameters: 'ACTION, SERVER_STATUS',
            choiceType: 'FORMATTED_HTML'
        ),
        
        // Hidden parameters that will be set by the HTML form controls
        booleanParam(name: 'INFRASTRUCTURE_BOOTSTRAPPING', defaultValue: false, description: ''),
        booleanParam(name: 'INFRASTRUCTURE_CONFIGURATION', defaultValue: false, description: ''),
        booleanParam(name: 'APPLICATION_DEPLOYMENT', defaultValue: false, description: ''),
        booleanParam(name: 'DESTROY_INFRASTRUCTURE', defaultValue: false, description: ''),
        string(name: 'DESTROY_CONFIRMATION', defaultValue: '', description: ''),
        booleanParam(name: 'START_SERVER', defaultValue: false, description: ''),
        booleanParam(name: 'STOP_SERVER', defaultValue: false, description: ''),
        booleanParam(name: 'DISPLAY_ADDRESSES', defaultValue: true, description: ''),
        
        // Common parameters always visible
        string(name: 'AWS_REGION', defaultValue: 'eu-west-1', description: 'AWS region to use (e.g., eu-west-1)'),
        choice(name: 'LOG_LEVEL', choices: ['INFO', 'DEBUG'], description: 'Log detail level - INFO or DEBUG'),
        string(name: 'CLUSTER_VERSION', defaultValue: '23.09', description: 'Cluster version for naocloud image')
    ])
])

pipeline {
    agent any
    
    options {
        disableConcurrentBuilds()
    }
    
    triggers {
        pollSCM('*/5 * * * *')
    }
    
    stages {
        stage('Display Instructions') {
            steps {
                script {
                    // Display helpful information about the current status and available actions
                    echo """
                    ============================================================
                    JENKINS CLOUD MANAGEMENT PANEL
                    ============================================================
                    
                    HOW TO USE THIS PIPELINE:
                    1. Select an ACTION from the dropdown
                    2. Check the appropriate options based on your needs
                    3. Click "Build" to execute the selected actions
                    
                    Current Selection: ${params.ACTION}
                    Current Server Status: ${params.SERVER_STATUS}
                    ============================================================
                    """
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
                        if (params.SERVER_STATUS == "NOT_CREATED") {
                            error("❌ Cannot manage server because it doesn't exist yet. Please create infrastructure first.")
                        }
                        
                        // Additional validations for specific states
                        if (params.START_SERVER && params.SERVER_STATUS == "RUNNING") {
                            error("❌ Cannot start server because it is already running.")
                        }
                        
                        if (params.STOP_SERVER && params.SERVER_STATUS == "STOPPED") {
                            error("❌ Cannot stop server because it is already stopped.")
                        }
                    }
                    
                    if (params.ACTION == 'View Information') {
                        // Verify server is running before attempting to display addresses
                        if (params.DISPLAY_ADDRESSES && (params.SERVER_STATUS == "NOT_CREATED" || params.SERVER_STATUS == "STOPPED")) {
                            error("❌ Cannot display addresses because the server is not running.")
                        }
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
                    sh '''
                        echo "Starting infrastructure bootstrapping process..."
                        echo "AWS Region: ${AWS_REGION}"
                        echo "Log Level: ${LOG_LEVEL}"
                        
                        if [ "${LOG_LEVEL}" = "DEBUG" ]; then
                            set -x  # Enable bash debug mode
                        fi
                        
                        terraform init
                        terraform plan -out=tfplan
                        terraform apply -auto-approve tfplan
                        
                        echo "Infrastructure bootstrapping completed successfully."
                    '''
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
                    sh '''
                        echo "Starting infrastructure configuration process..."
                        echo "AWS Region: ${AWS_REGION}"
                        echo "Log Level: ${LOG_LEVEL}"
                        
                        if [ "${LOG_LEVEL}" = "DEBUG" ]; then
                            set -x  # Enable bash debug mode
                        fi
                        
                        AWS_REGION=${AWS_REGION} ansible-inventory -i aws_ec2.yaml --list

                        ansible-playbook -i aws_ec2.yaml aws_playbook.yaml \
                            --private-key=/workspace/aws/id_rsa \
                            -e "ansible_ssh_common_args='-o StrictHostKeyChecking=no'" 
                        
                        ansible-playbook -i aws_ec2.yaml push_load_playbook-1.yaml \
                            --private-key=/workspace/aws/id_rsa \
                            -e "ansible_ssh_common_args='-o StrictHostKeyChecking=no'" \
                            -e "naocloud_tag=${CLUSTER_VERSION}" \
                            -e "naogizmo_tag=${CLUSTER_VERSION}"
                            
                        echo "Infrastructure configuration completed successfully."
                    '''
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
                    sh '''
                        echo "Starting application deployment process..."
                        echo "AWS Region: ${AWS_REGION}"
                        echo "Log Level: ${LOG_LEVEL}"
                        echo "Cluster Version: ${CLUSTER_VERSION}"
                        
                        if [ "${LOG_LEVEL}" = "DEBUG" ]; then
                            set -x  # Enable bash debug mode
                        fi
                        
                        ansible-playbook -i aws_ec2.yaml helm-playbook.yaml  \
                            --private-key=/workspace/aws/id_rsa \
                            -e "ansible_ssh_common_args='-o StrictHostKeyChecking=no'" 
                            
                        echo "Application deployment completed successfully."
                    '''
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
                    sh '''
                        echo "Starting infrastructure destruction process..."
                        echo "AWS Region: ${AWS_REGION}"
                        echo "Log Level: ${LOG_LEVEL}"
                        
                        if [ "${LOG_LEVEL}" = "DEBUG" ]; then
                            set -x  # Enable bash debug mode
                        fi
                        
                        terraform destroy -auto-approve
                        
                        echo "Infrastructure destroyed successfully."
                    '''
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
                    echo "Stopping server instances..."
                    echo "AWS Region: ${AWS_REGION}"
                    echo "Log Level: ${LOG_LEVEL}"
                    
                    if [ "${LOG_LEVEL}" = "DEBUG" ]; then
                        set -x  # Enable bash debug mode
                    fi
                    
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
                        echo "Stop command issued successfully."
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
                    echo "Starting server instances..."
                    echo "AWS Region: ${AWS_REGION}"
                    echo "Log Level: ${LOG_LEVEL}"
                    
                    if [ "${LOG_LEVEL}" = "DEBUG" ]; then
                        set -x  # Enable bash debug mode
                    fi
                    
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
                        echo "Start command issued successfully."
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
                    echo "=== Server Information ==="
                    echo "AWS Region: ${AWS_REGION}"
                    
                    echo "Master Instance Details:"
                    MASTER_DATA=$(aws ec2 describe-instances \
                        --filters "Name=tag:Name,Values=master_instance" "Name=instance-state-name,Values=running,pending" \
                        --query "Reservations[].Instances[].[InstanceId,PublicIpAddress,PublicDnsName,PrivateIpAddress]" \
                        --output text)
                    if [ -n "$MASTER_DATA" ]; then
                        i=0
                        echo "$MASTER_DATA" | while read -r id pub_ip pub_dns priv_ip; do
                            echo "Master $((i+=1)):"
                            echo "  Instance ID: $id"
                            echo "  Public IP: $pub_ip"
                            echo "  Public DNS: $pub_dns"
                            echo "  Private IP: $priv_ip"
                        done
                    else
                        echo "No running master instances found with tag Name=master_instance"
                    fi
                    
                    echo "Worker Instance Details:"
                    WORKER_DATA=$(aws ec2 describe-instances \
                        --filters "Name=tag:Name,Values=worker_instance" "Name=instance-state-name,Values=running,pending" \
                        --query "Reservations[].Instances[].[InstanceId,PublicIpAddress,PublicDnsName,PrivateIpAddress]" \
                        --output text)
                    if [ -n "$WORKER_DATA" ]; then
                        i=0
                        echo "$WORKER_DATA" | while read -r id pub_ip pub_dns priv_ip; do
                            echo "Worker $((i+=1)):"
                            echo "  Instance ID: $id"
                            echo "  Public IP: $pub_ip"
                            echo "  Public DNS: $pub_dns"
                            echo "  Private IP: $priv_ip"
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
        success {
            echo """
            ============================================================
            ✅ PIPELINE EXECUTION COMPLETED SUCCESSFULLY
            
            Action Performed: ${params.ACTION}
            ============================================================
            """
        }
        failure {
            echo """
            ============================================================
            ❌ PIPELINE EXECUTION FAILED
            
            Action Attempted: ${params.ACTION}
            Please check the logs above for details on what went wrong.
            ============================================================
            """
        }
    }
}