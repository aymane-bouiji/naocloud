pipeline {
    agent any
    
    parameters {
        choice(
            name: 'ACTION',
            choices: ['Infrastructure Configuration', 'Application Deployment', 'Stop running Server', 'Display Addresses', 'Destroy Infrastructure', 'Start Server'],
            description: 'Select an action to perform on the cloud infrastructure'
        )
    }
    
    stages {
        stage('Check Instance States') {
            steps {
                sh '''
                    set +x
                    echo "=== Checking Instance States ==="
                    # Your existing instance state checking script here
                    echo "=== Instance States Saved ==="
                '''
                
                script {
                    // Your existing script to read properties
                    props = readProperties file: 'serverState.properties'
                    echo "Current server state:"
                    echo "- Has instances: ${props.hasInstances}"
                    echo "- Has running instances: ${props.hasRunningInstances}"
                    echo "- Has stopped instances: ${props.hasStoppedInstances}"
                }
            }
        }
        
        stage('Display Server State') {
            steps {
                sh '''
                    set +x
                    echo "=== Server State ==="
                    # Your existing script to display server state
                    echo "==================="
                '''
                
                script {
                    def selectedAction = params.ACTION
                    echo "Selected action: ${selectedAction}"
                    
                    // Determine valid actions based on server state
                    def validActions = []
                    if (props.hasRunningInstances == 'true') {
                        validActions = ['Infrastructure Configuration', 'Application Deployment', 'Stop running Server', 'Display Addresses', 'Destroy Infrastructure']
                    } else if (props.hasStoppedInstances == 'true') {
                        validActions = ['Start Server', 'Destroy Infrastructure']
                    } else {
                        validActions = ['Infrastructure Configuration']
                    }
                    
                    echo "Valid actions for current state:"
                    validActions.each { action ->
                        echo "- ${action}"
                    }
                    
                    // Validate selected action
                    if (!selectedAction || !validActions.contains(selectedAction)) {
                        error "❌ The selected action '${selectedAction}' is not valid for the current server state. Please select one of: ${validActions.join(', ')}"
                    }
                }
            }
        }
        
        // The rest of your stages...
        stage('Parameter Validation') {
            steps {
                // Replace getContext() with a more specific implementation
                script {
                    echo "Validating parameters for action: ${params.ACTION}"
                    // Add your parameter validation logic here
                }
            }
        }
        
        stage('Execute Action') {
            steps {
                // Replace getContext() with a more specific implementation
                script {
                    echo "Executing action: ${params.ACTION}"
                    // Add your action execution logic here
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