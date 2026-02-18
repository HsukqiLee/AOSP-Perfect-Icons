
import os
from PIL import Image

def process_icons(input_dir, output_dir):
    if not os.path.exists(output_dir):
        os.makedirs(output_dir)

    processed_count = 0
    skipped_count = 0
    compressed_count = 0

    if not os.path.exists(input_dir):
        print(f"Error: Input directory '{input_dir}' not found.")
        return

    files = [f for f in os.listdir(input_dir) if f.lower().endswith('.png')]
    total_files = len(files)

    print(f"Found {total_files} PNG files in {input_dir}")

    for filename in files:
        input_path = os.path.join(input_dir, filename)
        output_path = os.path.join(output_dir, filename)

        try:
            with Image.open(input_path) as img:
                # Source Compression: Check if file size > 200KB (204800 bytes)
                file_size = os.path.getsize(input_path)
                if file_size > 204800:
                    print(f"Compressing large source file: {filename} ({file_size/1024:.2f} KB)")
                    # Save back to input_path with optimization
                    img.save(input_path, optimize=True)
                    compressed_count += 1
                
                # Pre-processing: If 512x518, crop bottom 6 pixels
                if img.size == (512, 518):
                    img = img.crop((0, 0, 512, 512))
                    print(f"Pre-processed (cropped bottom): {filename}")

                # Resize to 570x570 using LANCZOS resampling if available, else fallback
                resample_method = getattr(Image.Resampling, "LANCZOS", Image.LANCZOS)
                resized_img = img.resize((570, 570), resample=resample_method)

                # Crop center 512x512
                # Center coordinates
                center_x, center_y = 570 // 2, 570 // 2
                crop_size = 512
                left = center_x - crop_size // 2
                top = center_y - crop_size // 2
                right = center_x + crop_size // 2
                bottom = center_y + crop_size // 2

                cropped_img = resized_img.crop((left, top, right, bottom))
                
                # Save processed image with optimization
                cropped_img.save(output_path, optimize=True)
                processed_count += 1
                print(f"Processed: {filename}")

        except Exception as e:
            print(f"Error processing {filename}: {e}")
            skipped_count += 1

    print(f"\nProcessing complete.")
    print(f"Total processed: {processed_count}")
    print(f"Source files compressed: {compressed_count}")
    print(f"Skipped/Errors: {skipped_count}")

if __name__ == "__main__":
    current_dir = os.getcwd()
    input_directory = os.path.join(current_dir, "PixelLauncherMods")
    output_directory = os.path.join(current_dir, "GlobalIconPack")
    
    process_icons(input_directory, output_directory)
