def awsRegion = binding.hasVariable('AWS_REGION') ? binding.variables.get('AWS_REGION') : 'eu-west-1'
def command = """
    set +x
    export AWS_DEFAULT_REGION=${awsRegion}
    
    # Check master instances
    MASTER_DATA=\$(aws ec2 describe-instances \
        --filters "Name=tag:Name,Values=master_instance" \
        --query "Reservations[].Instances[].State.Name" \
        --output text 2>/dev/null || echo "")
    
    # Check worker instances
    WORKER_DATA=\$(aws ec2 describe-instances \
        --filters "Name=tag:Name,Values=worker_instance" \
        --query "Reservations[].Instances[].State.Name" \
        --output text 2>/dev/null || echo "")
    
    # Debug output (logged to console)
    echo "DEBUG: Master instance states: \$MASTER_DATA" >&2
    echo "DEBUG: Worker instance states: \$WORKER_DATA" >&2
    
    # Initialize state flags
    has_instances=false
    has_running=false
    has_stopped=false
    
    # Process all states
    for state in \$MASTER_DATA \$WORKER_DATA; do
        if [ -n "\$state" ]; then
            has_instances=true
            case "\$state" in
                "running"|"pending")
                    has_running=true
                    ;;
                "stopped")
                    has_stopped=true
                    ;;
            esac
        fi
    done
    
    # Debug flag values
    echo "DEBUG: has_instances=\$has_instances" >&2
    echo "DEBUG: has_running=\$has_running" >&2
    echo "DEBUG: has_stopped=\$has_stopped" >&2
    
    # Determine available actions
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
    if [ \${#actions[@]} -eq 0 ]; then
        echo "No valid actions available"
    else
        for action in "\${actions[@]}"; do
            echo "\$action"
        done
    fi
"""

// Execute the shell command
def process = ["bash", "-c", command].execute()
def stdout = new StringBuilder()
def stderr = new StringBuilder()
process.consumeProcessOutput(stdout, stderr)
process.waitForOrKill(10000) // Timeout after 10 seconds

// Log debug output to console
if (stderr) {
    println "DEBUG: Shell script stderr: ${stderr}"
}

// Parse stdout into a list of actions
def actions = stdout.toString().trim().split('\n').findAll { it }

// Return actions or fallback
return actions ?: ['No valid actions available']