param(
    [ValidateSet("selenium", "playwright")]
    [string]$Engine = "selenium",

    [string]$Browser = "chrome",

    [ValidateSet("feature", "excel", "both")]
    [string]$DataSource = "feature",

    [bool]$Headless = $false,
    [int]$Threads = 1
)

# Keep the first local run simple: one visible browser is much easier to debug.
$ParallelMode = if ($Threads -le 1) { "none" } else { "methods" }

mvn clean test `
    "-Dengine=$Engine" `
    "-Dbrowser=$Browser" `
    "-Ddata.source=$DataSource" `
    "-Dheadless=$($Headless.ToString().ToLower())" `
    "-Dparallel.mode=$ParallelMode" `
    "-Dthread.count=$([Math]::Max($Threads, 1))" `
    "-Ddataprovider.thread.count=$([Math]::Max($Threads, 1))"

exit $LASTEXITCODE
