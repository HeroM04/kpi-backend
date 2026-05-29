$transcriptPath = "C:\Users\Admin\.gemini\antigravity\brain\f61c5d41-15fa-4281-8aab-d89cb5832895\.system_generated\logs\transcript.jsonl"
$lines = [System.IO.File]::ReadAllLines($transcriptPath, [System.Text.Encoding]::UTF8)

# Let's extract all user prompts
$userRequests = @()
foreach ($line in $lines) {
    if ($line.Trim() -eq "") { continue }
    $obj = ConvertFrom-Json $line
    if ($obj.type -eq "USER_INPUT") {
        $userRequests += "=== STEP " + $obj.step_index + " (" + $obj.created_at + ") ==="
        $userRequests += $obj.content
        $userRequests += "`n`n"
    }
}

[System.IO.File]::WriteAllLines("d:\kpi-backend\all_user_requests.txt", $userRequests, [System.Text.Encoding]::UTF8)
Write-Output "Done extracting user requests!"
