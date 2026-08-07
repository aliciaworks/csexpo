# csexpo build orchestration (C# Export: Kotlin klib -> C#)
#
# Tasks:
#   klib     - compile the sample KMP library to a Kotlin/Native klib (downloads toolchain)
#   generate - run the csexpo generator (klib -> C# bindings in cs-consumer/Generated)
#   cs       - build the C# consumer
#   all      - klib + generate + cs

param(
    [string]$Task = "all"
)
$ErrorActionPreference = "Stop"
$root = $PSScriptRoot

function Invoke-Step {
    param([string]$Name, [scriptblock]$Body)
    Write-Host ""
    Write-Host "===== $Name =====" -ForegroundColor Cyan
    & $Body
    if ($LASTEXITCODE -ne 0) { throw "Step failed: $Name" }
}

function Find-Klib {
    # The compiled native klib is the directory (under build/classes) that contains 'default' + 'manifest'.
    $klibParent = "$root\kmp-lib\build\classes\kotlin\mingwX64\main\klib"
    $inner = Get-ChildItem $klibParent -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Path "$($_.FullName)\default" } |
        Select-Object -First 1
    if ($inner) { return $inner.FullName }
    # Fallback: any *.klib file
    $found = Get-ChildItem "$root\kmp-lib\build" -Recurse -Filter *.klib -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($found) { return $found.FullName }
    throw "Could not locate the compiled klib. Run '.\build.ps1 -Task klib' first."
}

switch ($Task.ToLower()) {
    "klib" {
        Invoke-Step -Name "Compile kmp-lib klib" -Body {
            Push-Location "$root\kmp-lib"
            .\gradlew.bat --no-daemon compileKotlinMingwX64
            Pop-Location
        }
    }
    "generate" {
        $klib = Find-Klib
        Invoke-Step -Name "Run generator on $klib" -Body {
            Push-Location "$root\generator"
            .\gradlew.bat --no-daemon run --args="--klib `"$klib`" --output `"$root\cs-consumer\Generated`""
            Pop-Location
        }
    }
    "cs" {
        Invoke-Step -Name "Build C# consumer" -Body {
            dotnet build "$root\cs-consumer\cs-consumer.csproj"
        }
    }
    "run" {
        Invoke-Step -Name "Run C# consumer" -Body {
            dotnet run --project "$root\cs-consumer\cs-consumer.csproj"
        }
    }
    "all" {
        & $PSCommandPath -Task klib
        & $PSCommandPath -Task generate
        & $PSCommandPath -Task cs
    }
    default {
        Write-Host "Unknown task: $Task" -ForegroundColor Yellow
        Write-Host "Valid tasks: klib, generate, cs, run, all"
    }
}
