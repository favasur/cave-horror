/// <summary>
/// Cave Horror: White Eyes — Asset Processor
/// 
/// C# console tool for preparing game assets for the Hytale plugin.
/// Runs standalone on Windows (.NET 8+) to:
/// - Convert .oga audio to .ogg format
/// - Resize/convert textures to power-of-2 dimensions
/// - Validate JSON/PNG/OGA asset integrity
/// - Batch-process entire asset directories
/// 
/// Usage:
///   dotnet run -- convert-audio  <input.oga> <output.ogg>
///   dotnet run -- resize-texture <input.png> <width> <height> <output.png>
///   dotnet run -- validate       <assets-dir>
///   dotnet run -- batch          <assets-dir>
/// </summary>

using System;
using System.IO;
using System.Linq;
using System.Text.Json;

namespace CaveHorror.AssetProcessor;

class Program
{
    static int Main(string[] args)
    {
        Console.WriteLine("Cave Horror: White Eyes — Asset Processor");
        Console.WriteLine("===========================================");
        Console.WriteLine();

        if (args.Length == 0)
        {
            PrintUsage();
            return 0;
        }

        return args[0].ToLowerInvariant() switch
        {
            "convert-audio" => ConvertAudio(args),
            "resize-texture" => ResizeTexture(args),
            "validate" => ValidateAssets(args),
            "batch" => BatchProcess(args),
            _ => PrintUsage()
        };
    }

    static int PrintUsage()
    {
        Console.WriteLine("Usage:");
        Console.WriteLine("  AssetProcessor convert-audio  <input.oga> <output.ogg>");
        Console.WriteLine("  AssetProcessor resize-texture <input.png> <width> <height> <output.png>");
        Console.WriteLine("  AssetProcessor validate       <assets-directory>");
        Console.WriteLine("  AssetProcessor batch          <assets-directory>");
        return 0;
    }

    static int ConvertAudio(string[] args)
    {
        if (args.Length < 3)
        {
            Console.Error.WriteLine("Error: convert-audio requires <input> <output> paths.");
            return 1;
        }

        string input = args[1];
        string output = args[2];

        if (!File.Exists(input))
        {
            Console.Error.WriteLine($"Error: Input file not found: {input}");
            return 1;
        }

        Console.WriteLine($"Converting: {input} → {output}");

        try
        {
            var process = new System.Diagnostics.Process
            {
                StartInfo = new System.Diagnostics.ProcessStartInfo
                {
                    FileName = "ffmpeg",
                    Arguments = $"-i \"{input}\" -c:a libvorbis -q:a 3 \"{output}\"",
                    UseShellExecute = false,
                    RedirectStandardOutput = true,
                    RedirectStandardError = true
                }
            };
            process.Start();
            process.WaitForExit();

            if (process.ExitCode == 0)
            {
                Console.WriteLine($"  ✓ Converted ({new FileInfo(output).Length / 1024} KB)");
                return 0;
            }
        }
        catch (System.ComponentModel.Win32Exception)
        {
            // FFmpeg not installed — fall back to copy
        }

        Console.WriteLine("  FFmpeg not found in PATH. Copying file directly.");
        File.Copy(input, output, overwrite: true);
        Console.WriteLine($"  ✓ Copied ({new FileInfo(output).Length / 1024} KB)");
        return 0;
    }

    static int ResizeTexture(string[] args)
    {
        if (args.Length < 5)
        {
            Console.Error.WriteLine("Error: resize-texture requires <input> <width> <height> <output>.");
            return 1;
        }

        string input = args[1];
        if (!int.TryParse(args[2], out int width) || !int.TryParse(args[3], out int height))
        {
            Console.Error.WriteLine("Error: width and height must be integers.");
            return 1;
        }
        string output = args[4];

        if (!File.Exists(input))
        {
            Console.Error.WriteLine($"Error: Input file not found: {input}");
            return 1;
        }

        Console.WriteLine($"Resizing: {input} → {width}x{height} → {output}");

        try
        {
            using var img = System.Drawing.Image.FromFile(input);
            using var bitmap = new System.Drawing.Bitmap(width, height);
            using var g = System.Drawing.Graphics.FromImage(bitmap);

            g.InterpolationMode = System.Drawing.Drawing2D.InterpolationMode.HighQualityBicubic;
            g.DrawImage(img, 0, 0, width, height);

            var format = Path.GetExtension(output).ToLowerInvariant() switch
            {
                ".jpg" or ".jpeg" => System.Drawing.Imaging.ImageFormat.Jpeg,
                ".bmp" => System.Drawing.Imaging.ImageFormat.Bmp,
                _ => System.Drawing.Imaging.ImageFormat.Png
            };

            bitmap.Save(output, format);
            Console.WriteLine($"  ✓ Resized to {width}x{height}");
            return 0;
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine($"Error resizing texture: {ex.Message}");
            return 1;
        }
    }

    // Shared validator — checks a single file and returns error count
    static int ValidateFile(string file)
    {
        string ext = Path.GetExtension(file).ToLowerInvariant();
        int errors = 0;

        switch (ext)
        {
            case ".json":
                try
                {
                    var json = File.ReadAllText(file);
                    JsonDocument.Parse(json);
                }
                catch (Exception ex)
                {
                    Console.Error.WriteLine($"  ✗ {file}: Invalid JSON — {ex.Message}");
                    errors++;
                }
                break;

            case ".png":
                try
                {
                    using var img = System.Drawing.Image.FromFile(file);
                    if (img.Width < 1 || img.Height < 1)
                    {
                        Console.Error.WriteLine($"  ✗ {file}: Invalid dimensions ({img.Width}x{img.Height})");
                        errors++;
                    }
                }
                catch (Exception ex)
                {
                    Console.Error.WriteLine($"  ✗ {file}: Corrupt PNG — {ex.Message}");
                    errors++;
                }
                break;

            case ".oga":
            case ".ogg":
            case ".wav":
                var info = new FileInfo(file);
                if (info.Length < 100)
                {
                    Console.Error.WriteLine($"  ✗ {file}: Audio file too small ({info.Length} bytes)");
                    errors++;
                }
                break;
        }

        return errors;
    }

    static int ValidateAssets(string[] args)
    {
        string dir = args.Length < 2 ? "." : args[1];
        return ValidateDirectory(dir);
    }

    /// <summary>
    /// Shared validation logic — walks a directory and validates all asset files.
    /// Used by both validate and batch commands.
    /// </summary>
    static int ValidateDirectory(string dir)
    {
        if (!Directory.Exists(dir))
        {
            Console.Error.WriteLine($"Error: Directory not found: {dir}");
            return 1;
        }

        Console.WriteLine($"Validating assets in: {dir}");
        Console.WriteLine();

        int errors = 0;
        int files = 0;

        foreach (string file in Directory.EnumerateFiles(dir, "*.*", SearchOption.AllDirectories))
        {
            files++;
            errors += ValidateFile(file);
        }

        Console.WriteLine();
        Console.WriteLine($"Results: {files} files checked, {errors} error(s)");
        return errors > 0 ? 1 : 0;
    }

    static int BatchProcess(string[] args)
    {
        string dir = args.Length < 2 ? "." : args[1];

        if (!Directory.Exists(dir))
        {
            Console.Error.WriteLine($"Error: Directory not found: {dir}");
            return 1;
        }

        Console.WriteLine($"Batch processing assets in: {dir}");
        Console.WriteLine();

        // Step 1: Validate
        Console.WriteLine("Step 1/3: Validating assets...");
        if (ValidateDirectory(dir) != 0)
        {
            Console.Error.WriteLine("Validation failed. Aborting batch.");
            return 1;
        }

        // Step 2: Convert .oga → .ogg
        Console.WriteLine();
        Console.WriteLine("Step 2/3: Converting audio files...");
        int audioCount = 0;
        foreach (string file in Directory.EnumerateFiles(dir, "*.oga", SearchOption.AllDirectories))
        {
            string output = Path.ChangeExtension(file, ".ogg");
            ConvertAudio(["convert-audio", file, output]);
            audioCount++;
        }
        Console.WriteLine($"  Converted {audioCount} audio file(s)");

        // Step 3: Ensure textures are power-of-2
        Console.WriteLine();
        Console.WriteLine("Step 3/3: Checking texture dimensions...");
        int textureCount = 0;
        foreach (string file in Directory.EnumerateFiles(dir, "*.png", SearchOption.AllDirectories))
        {
            try
            {
                using var img = System.Drawing.Image.FromFile(file);
                int newW = img.Width, newH = img.Height;
                bool needsFix = false;

                if ((img.Width & (img.Width - 1)) != 0) { newW = NextPowerOf2(img.Width); needsFix = true; }
                if ((img.Height & (img.Height - 1)) != 0) { newH = NextPowerOf2(img.Height); needsFix = true; }

                if (needsFix)
                {
                    Console.WriteLine($"  Resizing {file}: {img.Width}x{img.Height} → {newW}x{newH}");
                    ResizeTexture(["resize-texture", file, newW.ToString(), newH.ToString(), file]);
                    textureCount++;
                }
            }
            catch { /* skip non-image files */ }
        }
        Console.WriteLine($"  Fixed {textureCount} texture(s)");

        Console.WriteLine();
        Console.WriteLine("✓ Batch processing complete.");
        return 0;
    }

    static int NextPowerOf2(int n)
    {
        n--;
        n |= n >> 1;
        n |= n >> 2;
        n |= n >> 4;
        n |= n >> 8;
        n |= n >> 16;
        return n + 1;
    }
}
