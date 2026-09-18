param(
    [string]$ProjectRoot = "c:\K3_Firehose\projects\k3-diegetic"
)

$JarPath = Join-Path $ProjectRoot "build\libs\k3-diegetic-1.0.0.jar"
$SourcesPath = Join-Path $ProjectRoot "build\libs\k3-diegetic-1.0.0-sources.jar"

Add-Type -AssemblyName System.IO.Compression.FileSystem

Write-Host "============================================================"
Write-Host "          K3 DIEGETIC JAR ARTIFACT AUDIT"
Write-Host "============================================================"

$jarFile = Get-Item $JarPath
$sourcesFile = Get-Item $SourcesPath

Write-Host "JAR Path:         $($jarFile.FullName)"
Write-Host "JAR Size:         $($jarFile.Length) bytes"
Write-Host "Sources JAR Path: $($sourcesFile.FullName)"
Write-Host "Sources Size:     $($sourcesFile.Length) bytes"

$zip = [System.IO.Compression.ZipFile]::OpenRead($JarPath)
$entries = $zip.Entries | ForEach-Object { $_.FullName }

Write-Host "`n--- Checking Required Classes ---"
$requiredClasses = @(
    "com/k3/diegetic/K3DiegeticMod.class",
    "com/k3/diegetic/client/K3DiegeticClient.class",
    "com/k3/diegetic/data/K3DiegeticDataGenerator.class",
    "com/k3/diegetic/component/WorkstationStateComponent.class",
    "com/k3/diegetic/component/ModDataComponentTypes.class",
    "com/k3/diegetic/block/ModBlocks.class",
    "com/k3/diegetic/block/ArtisanAnvilBlock.class",
    "com/k3/diegetic/block/entity/ArtisanAnvilBlockEntity.class",
    "com/k3/diegetic/recipe/ModRecipes.class",
    "com/k3/diegetic/recipe/ArtisanCraftingRecipe.class"
)

$allClassesFound = $true
foreach ($rc in $requiredClasses) {
    $found = $entries -contains $rc
    if ($found) {
        Write-Host "  [PASS] $rc" -ForegroundColor Green
    } else {
        Write-Host "  [FAIL] $rc" -ForegroundColor Red
        $allClassesFound = $false
    }
}

Write-Host "`n--- Checking Required Assets, Data & Manifest ---"
$requiredResources = @(
    "assets/k3_diegetic/icon.png",
    "assets/k3_diegetic/blockstates/artisan_anvil.json",
    "assets/k3_diegetic/models/block/artisan_anvil.json",
    "assets/k3_diegetic/models/item/artisan_anvil.json",
    "assets/k3_diegetic/lang/en_us.json",
    "data/k3_diegetic/recipe/sword_forging.json",
    "data/k3_diegetic/recipe/pickaxe_forging.json",
    "fabric.mod.json"
)

$allResourcesFound = $true
foreach ($rr in $requiredResources) {
    $found = $entries -contains $rr
    if ($found) {
        Write-Host "  [PASS] $rr" -ForegroundColor Green
    } else {
        Write-Host "  [FAIL] $rr" -ForegroundColor Red
        $allResourcesFound = $false
    }
}

Write-Host "`n--- Checking fabric.mod.json Version Expansion ---"
$modJsonEntry = $zip.Entries | Where-Object { $_.FullName -eq "fabric.mod.json" }
$reader = New-Object System.IO.StreamReader($modJsonEntry.Open())
$modJsonText = $reader.ReadToEnd()
$reader.Close()
$zip.Dispose()

$parsed = $modJsonText | ConvertFrom-Json
Write-Host "Mod ID:      $($parsed.id)"
Write-Host "Version:     $($parsed.version)"
Write-Host "Name:        $($parsed.name)"
Write-Host "Description: $($parsed.description)"

$versionOk = ($parsed.version -eq "1.0.0")
if ($versionOk) {
    Write-Host "  [PASS] Version expanded to 1.0.0 (no placeholder)" -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Version placeholder unexpanded: $($parsed.version)" -ForegroundColor Red
}

Write-Host "`n============================================================"
if ($allClassesFound -and $allResourcesFound -and $versionOk -and (Test-Path $SourcesPath)) {
    Write-Host "ALL ARTIFACT INSPECTION CHECKS PASSED (10/10 Classes, 8/8 Resources, Version 1.0.0, Sources JAR present)" -ForegroundColor Green
    exit 0
} else {
    Write-Host "ONE OR MORE ARTIFACT CHECKS FAILED" -ForegroundColor Red
    exit 1
}
