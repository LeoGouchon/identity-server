$ErrorActionPreference = 'Stop'

# Prepares the git-ignored local files of a worktree (used by `make install`).
# Each file is copied from the main checkout when available, otherwise created from its example.

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$gitCommonDir = git -C $repoRoot rev-parse --path-format=absolute --git-common-dir
if ($LASTEXITCODE -ne 0)
{
    throw "Unable to locate the git common directory."
}
$mainCheckout = Split-Path $gitCommonDir -Parent

function Install-LocalFile
{
    param(
        [string] $RelativePath,
        [string] $ExamplePath
    )

    $target = Join-Path $repoRoot $RelativePath
    if (Test-Path $target)
    {
        Write-Host "Using existing $RelativePath."
        return
    }

    $source = Join-Path $mainCheckout $RelativePath
    if (Test-Path $source)
    {
        Copy-Item $source $target
        Write-Host "Copied $RelativePath from main checkout ($mainCheckout)."
        return
    }

    Copy-Item (Join-Path $repoRoot $ExamplePath) $target
    Write-Host "Created $RelativePath from $ExamplePath. Review it before starting."
}

Install-LocalFile "config/config.yml" "config/config.example.yml"
Install-LocalFile "frontend/.env" "frontend/.env.example"
