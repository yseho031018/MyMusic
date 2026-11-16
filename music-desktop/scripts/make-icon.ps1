Add-Type -AssemblyName System.Drawing
$size = 256
$bitmap = New-Object System.Drawing.Bitmap $size, $size
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$graphics.Clear([System.Drawing.Color]::FromArgb(255, 22, 22, 29))

$edge = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255, 201, 169, 255), 8)
$graphics.DrawEllipse($edge, 18, 18, 220, 220)
$vinyl = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 24, 24, 30))
$graphics.FillEllipse($vinyl, 36, 36, 184, 184)
$groove = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255, 58, 56, 68), 2)
foreach ($radius in 92, 112, 132, 152, 172) {
  $origin = (256 - $radius) / 2
  $graphics.DrawEllipse($groove, $origin, $origin, $radius, $radius)
}
$label = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 201, 169, 255))
$graphics.FillEllipse($label, 92, 92, 72, 72)
$hole = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 22, 22, 29))
$graphics.FillEllipse($hole, 118, 118, 20, 20)

$assets = Join-Path $PSScriptRoot '..\assets'
New-Item -ItemType Directory -Force -Path $assets | Out-Null
$bitmap.Save((Join-Path $assets 'icon.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$graphics.Dispose()
$bitmap.Dispose()
$edge.Dispose()
$vinyl.Dispose()
$groove.Dispose()
$label.Dispose()
$hole.Dispose()
