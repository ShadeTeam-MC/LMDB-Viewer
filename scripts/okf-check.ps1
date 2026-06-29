<#
.SYNOPSIS
    Local Open Knowledge Format (OKF v0.1) conformance checker for the docs/ bundle.

.DESCRIPTION
    Verifies that the docs/ knowledge bundle is OKF v0.1 conformant:
      * every non-reserved .md has YAML frontmatter with a non-empty 'type';
      * the bundle-root index.md declares okf_version; other reserved files
        (index.md / log.md) carry no frontmatter;
      * every expected concept document is present (domain coverage);
      * every internal cross-link (in the bundle and in CLAUDE.md / README.md) resolves;
      * no Cyrillic characters appear in the documentation.

    Exits 0 on PASS, 1 on FAIL. Intended to run in CI or before committing doc changes.

.EXAMPLE
    pwsh scripts/okf-check.ps1
#>
param(
    [string]$Repo = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
)

$ErrorActionPreference = "Stop"
$bundle = Join-Path $Repo "docs"
$errors = New-Object System.Collections.Generic.List[string]
$warnings = New-Object System.Collections.Generic.List[string]
$reserved = @("index.md","log.md")

function Get-Frontmatter([string]$text) {
    # returns the YAML block (without fences) or $null
    if ($text -match "(?s)^﻿?---\r?\n(.*?)\r?\n---\r?\n") { return $Matches[1] }
    return $null
}

# ---- 1. Conformance: frontmatter + non-empty type on every non-reserved doc ----
$mdFiles = Get-ChildItem -Path $bundle -Recurse -Filter *.md
$conceptCount = 0
foreach ($f in $mdFiles) {
    $rel = $f.FullName.Substring($bundle.Length+1).Replace('\','/')
    $text = Get-Content -Raw $f.FullName
    $isReserved = $reserved -contains $f.Name
    $fm = Get-Frontmatter $text
    if ($isReserved) {
        if ($f.Name -eq "index.md" -and $rel -eq "index.md") {
            # root index.md: frontmatter allowed, must carry okf_version
            if (-not $fm) { $errors.Add("root index.md missing okf_version frontmatter") }
            elseif ($fm -notmatch "(?m)^okf_version\s*:") { $errors.Add("root index.md frontmatter has no okf_version") }
        } else {
            if ($fm) { $warnings.Add("$rel : reserved file has frontmatter (spec: none permitted)") }
        }
        continue
    }
    $conceptCount++
    if (-not $fm) { $errors.Add("$rel : missing YAML frontmatter"); continue }
    $typeMatch = [regex]::Match($fm, "(?m)^type\s*:\s*(\S.*?)\s*$")
    if (-not $typeMatch.Success) { $errors.Add("$rel : frontmatter has no non-empty 'type'") }
}

# ---- 2. Coverage: every expected concept is present ----
$expected = @(
  "overview.md","features.md","lmdb-concepts.md","conventions.md","roadmap.md",
  "architecture/overview.md","architecture/access-layer.md","architecture/decode-layer.md",
  "architecture/ui-layer.md","architecture/native-loading.md","architecture/decoder-extension-point.md",
  "operations/build-run-test.md","playbooks/workflow-orchestration.md","playbooks/task-management.md"
)
foreach ($e in $expected) {
    if (-not (Test-Path (Join-Path $bundle $e))) { $errors.Add("MISSING expected concept: $e") }
}

# ---- 3. Cross-link resolution (bundle docs + CLAUDE.md + README.md) ----
$linkScan = @()
$linkScan += $mdFiles
$linkScan += Get-Item (Join-Path $Repo "CLAUDE.md")
$linkScan += Get-Item (Join-Path $Repo "README.md")
$linkRe = [regex]'\[(?:[^\]]*)\]\(([^)]+)\)'
$linkCount = 0
foreach ($f in $linkScan) {
    $inBundle = $f.FullName.StartsWith($bundle)
    $text = Get-Content -Raw $f.FullName
    foreach ($m in $linkRe.Matches($text)) {
        $target = $m.Groups[1].Value.Trim()
        if ($target -match '^(https?:|mailto:)') { continue }
        $target = ($target -split '#',2)[0]
        if ([string]::IsNullOrWhiteSpace($target)) { continue }
        $linkCount++
        if ($target.StartsWith('/')) {
            $resolved = Join-Path $bundle ($target.TrimStart('/'))
        } else {
            $resolved = Join-Path $f.Directory.FullName $target
        }
        try { $full = [System.IO.Path]::GetFullPath($resolved) } catch { $full = $resolved }
        if (-not (Test-Path $full)) {
            $where = if ($inBundle) { "docs/" + $f.FullName.Substring($bundle.Length+1).Replace('\','/') } else { $f.Name }
            $errors.Add("BROKEN LINK in ${where}: '$($m.Groups[1].Value.Trim())' -> $full")
        }
    }
}

# ---- 4. No Cyrillic anywhere in docs + CLAUDE.md + README.md ----
$cyr = [regex]'[Ѐ-ӿ]'
foreach ($f in $linkScan) {
    $text = Get-Content -Raw $f.FullName
    if ($cyr.IsMatch($text)) {
        $errors.Add("CYRILLIC characters found in $($f.FullName)")
    }
}

# ---- Report ----
Write-Output "OKF conformance check"
Write-Output "  bundle root : $bundle"
Write-Output "  concept docs: $conceptCount    expected: $($expected.Count)"
Write-Output "  links checked (internal): $linkCount"
Write-Output ""
if ($warnings.Count -gt 0) {
    Write-Output "WARNINGS ($($warnings.Count)):"
    $warnings | ForEach-Object { Write-Output "  - $_" }
    Write-Output ""
}
if ($errors.Count -eq 0) {
    Write-Output "RESULT: PASS - bundle is OKF v0.1 conformant, all cross-links resolve, all domains covered, no Cyrillic."
    exit 0
} else {
    Write-Output "RESULT: FAIL ($($errors.Count) error(s)):"
    $errors | ForEach-Object { Write-Output "  - $_" }
    exit 1
}
