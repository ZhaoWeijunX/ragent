param(
    [string]$KnowledgeBaseRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
$contentDirectories = Get-ChildItem -LiteralPath $KnowledgeBaseRoot -Directory |
    Where-Object { $_.Name -match '^\d{2}-' }
$files = $contentDirectories | ForEach-Object {
    Get-ChildItem -LiteralPath $_.FullName -Filter '*.md' -File
} | Sort-Object FullName

$issues = [System.Collections.Generic.List[object]]::new()
$documentHeadings = @{}
$rows = foreach ($file in $files) {
    $text = Get-Content -LiteralPath $file.FullName -Raw
    $lines = @(Get-Content -LiteralPath $file.FullName)
    $h1 = @([regex]::Matches($text, '(?m)^# (.+)$'))
    $frontmatterTitle = [regex]::Match($text, '(?m)^title:\s*(.+)$').Groups[1].Value.Trim()
    $prefix = [regex]::Match($file.Name, '^(\d{2}-\d{2})-').Groups[1].Value
    $h1Title = if ($h1.Count -eq 1) { $h1[0].Groups[1].Value.Trim() } else { '' }
    $normalizedFileTitle = [regex]::Replace([System.IO.Path]::GetFileNameWithoutExtension($file.Name), '[^\p{L}\p{Nd}]', '')
    $normalizedH1Title = [regex]::Replace($h1Title, '[^\p{L}\p{Nd}]', '')
    $h2Count = ([regex]::Matches($text, '(?m)^## ')).Count
    $mermaidOpen = ([regex]::Matches($text, '(?m)^```mermaid\s*$')).Count
    $fenceCount = ([regex]::Matches($text, '(?m)^```')).Count
    $chineseChars = ([regex]::Matches($text, '[\p{IsCJKUnifiedIdeographs}]')).Count

    if ($h1.Count -ne 1) {
        $issues.Add([pscustomobject]@{ Severity = 'ERROR'; File = $file.FullName; Issue = "H1 count=$($h1.Count)" })
    }
    if ($prefix -and -not $h1Title.StartsWith($prefix)) {
        $issues.Add([pscustomobject]@{ Severity = 'ERROR'; File = $file.FullName; Issue = 'Filename prefix and H1 mismatch' })
    }
    if ($normalizedFileTitle -ne $normalizedH1Title) {
        $issues.Add([pscustomobject]@{ Severity = 'ERROR'; File = $file.FullName; Issue = 'Filename topic and H1 mismatch' })
    }
    if ($frontmatterTitle -and $frontmatterTitle -ne $h1Title) {
        $issues.Add([pscustomobject]@{ Severity = 'ERROR'; File = $file.FullName; Issue = 'Frontmatter title and H1 mismatch' })
    }
    if ($h2Count -lt 6) {
        $issues.Add([pscustomobject]@{ Severity = 'WARN'; File = $file.FullName; Issue = "Only $h2Count H2 sections" })
    }
    if ($chineseChars -lt 800) {
        $issues.Add([pscustomobject]@{ Severity = 'WARN'; File = $file.FullName; Issue = "Chinese chars=$chineseChars, below 800" })
    }
    if ($fenceCount % 2 -ne 0) {
        $issues.Add([pscustomobject]@{ Severity = 'ERROR'; File = $file.FullName; Issue = 'Unbalanced code fences' })
    }
    if ($mermaidOpen -lt 1) {
        $issues.Add([pscustomobject]@{ Severity = 'WARN'; File = $file.FullName; Issue = 'No Mermaid diagram' })
    }
    if ($text -match '(?im)\b(TODO|TBD)\b|待补充|占位文档') {
        $issues.Add([pscustomobject]@{ Severity = 'WARN'; File = $file.FullName; Issue = 'Possible placeholder text' })
    }

    $requiredTopicPatterns = [ordered]@{
        Background = '(?m)^## .*(背景|解决的问题)'
        Code = '(?m)^## .*核心代码'
        Flow = '(?m)^## .*(完整流程|调用流程|处理流程|调用关系|时序)'
        Principle = '(?m)^## .*(实现原理|设计原因|为什么采用|核心机制)'
        Detail = '(?m)^## .*技术细节'
        Boundary = '(?m)^## .*(异常|并发|边界)'
        Improvement = '(?m)^## .*(问题|优化方向)'
    }
    foreach ($requiredTopic in $requiredTopicPatterns.GetEnumerator()) {
        if ($text -notmatch $requiredTopic.Value) {
            $issues.Add([pscustomobject]@{ Severity = 'WARN'; File = $file.FullName; Issue = "Missing required topic heading: $($requiredTopic.Key)" })
        }
    }

    foreach ($match in [regex]::Matches($text, '\[[^\]]+\]\(([^)]+\.md)(?:#[^)]+)?\)')) {
        $target = $match.Groups[1].Value
        if ($target -match '^[a-zA-Z]+://') { continue }
        $resolved = [System.IO.Path]::GetFullPath((Join-Path $file.DirectoryName $target))
        if (-not (Test-Path -LiteralPath $resolved)) {
            $issues.Add([pscustomobject]@{ Severity = 'ERROR'; File = $file.FullName; Issue = "Broken link: $target" })
        }
    }

    [pscustomobject]@{
        File = $file.FullName.Substring($KnowledgeBaseRoot.Length + 1)
        Lines = $lines.Count
        NonBlankLines = @($lines | Where-Object { $_.Trim().Length -gt 0 }).Count
        Characters = $text.Length
        ChineseCharacters = $chineseChars
        H2Sections = $h2Count
        Mermaid = $mermaidOpen
    }

    if ($h1Title) {
        $documentHeadings[$file.FullName] = $h1Title
    }
}

$indexPath = Join-Path $KnowledgeBaseRoot 'INDEX.md'
if (-not (Test-Path -LiteralPath $indexPath)) {
    $issues.Add([pscustomobject]@{ Severity = 'ERROR'; File = $indexPath; Issue = 'INDEX.md is missing' })
} else {
    $indexText = Get-Content -LiteralPath $indexPath -Raw
    $indexedTargets = [System.Collections.Generic.List[string]]::new()
    foreach ($match in [regex]::Matches($indexText, '\[([^\]]+)\]\(([^)]+\.md)(?:#[^)]+)?\)')) {
        $label = $match.Groups[1].Value.Trim()
        $target = $match.Groups[2].Value
        if ($target -match '^[a-zA-Z]+://') { continue }
        $resolved = [System.IO.Path]::GetFullPath((Join-Path $KnowledgeBaseRoot $target))
        if (-not (Test-Path -LiteralPath $resolved)) {
            $issues.Add([pscustomobject]@{ Severity = 'ERROR'; File = $indexPath; Issue = "Broken INDEX link: $target" })
            continue
        }
        if ($documentHeadings.ContainsKey($resolved)) {
            $indexedTargets.Add($resolved)
            if ($label -ne $documentHeadings[$resolved]) {
                $issues.Add([pscustomobject]@{ Severity = 'ERROR'; File = $indexPath; Issue = "INDEX label mismatch: $target" })
            }
        }
    }
    foreach ($file in $files) {
        $count = @($indexedTargets | Where-Object { $_ -eq $file.FullName }).Count
        if ($count -ne 1) {
            $issues.Add([pscustomobject]@{ Severity = 'ERROR'; File = $indexPath; Issue = "Document indexed $count times: $($file.Name)" })
        }
    }
}

foreach ($rootFile in Get-ChildItem -LiteralPath $KnowledgeBaseRoot -Filter '*.md' -File) {
    $rootText = Get-Content -LiteralPath $rootFile.FullName -Raw
    foreach ($match in [regex]::Matches($rootText, '\[[^\]]+\]\(([^)]+\.md)(?:#[^)]+)?\)')) {
        $target = $match.Groups[1].Value
        if ($target -match '^[a-zA-Z]+://') { continue }
        $resolved = [System.IO.Path]::GetFullPath((Join-Path $KnowledgeBaseRoot $target))
        if (-not (Test-Path -LiteralPath $resolved)) {
            $issues.Add([pscustomobject]@{ Severity = 'ERROR'; File = $rootFile.FullName; Issue = "Broken root link: $target" })
        }
    }
}

$summary = [pscustomobject]@{
    DocumentCount = $rows.Count
    TotalLines = ($rows | Measure-Object Lines -Sum).Sum
    TotalNonBlankLines = ($rows | Measure-Object NonBlankLines -Sum).Sum
    TotalCharacters = ($rows | Measure-Object Characters -Sum).Sum
    TotalChineseCharacters = ($rows | Measure-Object ChineseCharacters -Sum).Sum
    AverageLines = [math]::Round(($rows | Measure-Object Lines -Average).Average, 1)
    MinCharacters = ($rows | Measure-Object Characters -Minimum).Minimum
    MaxCharacters = ($rows | Measure-Object Characters -Maximum).Maximum
    MinChineseCharacters = ($rows | Measure-Object ChineseCharacters -Minimum).Minimum
    MaxChineseCharacters = ($rows | Measure-Object ChineseCharacters -Maximum).Maximum
    AverageChineseCharacters = [math]::Round(($rows | Measure-Object ChineseCharacters -Average).Average, 1)
    ErrorCount = @($issues | Where-Object Severity -eq 'ERROR').Count
    WarningCount = @($issues | Where-Object Severity -eq 'WARN').Count
}

[pscustomobject]@{
    Summary = $summary
    Documents = @($rows)
    Issues = @($issues)
} | ConvertTo-Json -Depth 6
