
import os
import json
from PIL import Image


OUTPUT_SIZE = 512
SOURCE_COMPRESSION_THRESHOLD = 204800
SOURCE_COMPRESSION_CACHE = ".process_icons_compression_cache.json"
TRIM_ALPHA_THRESHOLD = 16


def _trim_bounds_by_alpha(img, alpha_threshold=0):
    """Return explicit left, top, right, bottom content bounds from alpha values."""
    if img.mode != "RGBA":
        img = img.convert("RGBA")

    alpha = img.getchannel("A")
    width, height = img.size

    def is_visible(x, y):
        return alpha.getpixel((x, y)) > alpha_threshold

    left = None
    for x in range(width):
        if any(is_visible(x, y) for y in range(height)):
            left = x
            break

    if left is None:
        return None

    right = None
    for x in range(width - 1, -1, -1):
        if any(is_visible(x, y) for y in range(height)):
            right = x + 1
            break

    top = None
    for y in range(height):
        if any(is_visible(x, y) for x in range(width)):
            top = y
            break

    bottom = None
    for y in range(height - 1, -1, -1):
        if any(is_visible(x, y) for x in range(width)):
            bottom = y + 1
            break

    if None in (left, top, right, bottom):
        return None

    return left, top, right, bottom


def _alpha_bbox(img, alpha_threshold=0):
    """Return the alpha bounding box after optionally filtering faint pixels."""
    bounds = _trim_bounds_by_alpha(img, alpha_threshold=alpha_threshold)
    if bounds is None:
        return None

    return bounds


def _derive_layout_adjustments(img, alpha_threshold=2):
    """Derive a safe bleed ratio and vertical bias from the source image padding."""
    bbox = _alpha_bbox(img, alpha_threshold=alpha_threshold)
    if bbox is None:
        return 1.0, 0

    src_w, src_h = img.size
    left, top, right, bottom = bbox

    margin_left = left
    margin_top = top
    margin_right = src_w - right
    margin_bottom = src_h - bottom

    horizontal_padding = (margin_left + margin_right) / max(1, src_w)
    vertical_padding = (margin_top + margin_bottom) / max(1, src_h)
    padding_score = (horizontal_padding + vertical_padding) / 2.0

    # More transparent padding means we can safely enlarge a little, but keep
    # the curve gentle so the icon body does not get pushed into launcher masks.
    content_ratio = 1.0 + min(0.025, max(0.0, 0.008 + padding_score * 0.06))

    # Bias downward when the source has more bottom padding. Keep the shift
    # small so we do not recreate the top clipping that happened with larger
    # offsets.
    y_center_offset = (margin_bottom - margin_top) / 2.0
    center_y_bias = int(round(y_center_offset * max(0.08, 0.12 + padding_score * 0.03)))
    center_y_bias = max(-4, min(8, center_y_bias))

    return content_ratio, center_y_bias


def _trim_transparent(img, alpha_threshold=0):
    """Crop transparent padding based on alpha channel bounds."""
    bbox = _alpha_bbox(img, alpha_threshold=alpha_threshold)
    if bbox is None:
        if img.mode != "RGBA":
            img = img.convert("RGBA")
        return img
    return img.crop(bbox)


def _compose_centered(img, canvas_size=OUTPUT_SIZE, content_ratio=1.0, center_y_bias=0):
    """Center the trimmed icon on a 512x512 canvas without scaling."""
    src_w, src_h = img.size
    if src_w == 0 or src_h == 0:
        return Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))

    offset_x = max(0, (canvas_size - src_w) // 2)
    offset_y = max(0, (canvas_size - src_h) // 2)

    canvas = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    canvas.paste(img, (offset_x, offset_y), img)
    return canvas


def _center_offset_by_alpha(img, canvas_size, center_y_bias=0):
    """Center by visible bounds so the icon stays inside the square frame."""
    bbox = _alpha_bbox(img)
    if bbox is None:
        return (canvas_size - img.size[0]) // 2, (canvas_size - img.size[1]) // 2

    left, top, right, bottom = bbox
    content_center_x = (left + right) / 2.0
    content_center_y = (top + bottom) / 2.0

    offset_x = int(round((canvas_size / 2.0) - content_center_x))
    offset_y = int(round((canvas_size / 2.0) - content_center_y + center_y_bias))
    return offset_x, offset_y


def _sample_matte_color(img):
    """Pick a soft matte from edge pixels to replace transparency with a non-black fill."""
    rgba = img.convert("RGBA")
    width, height = rgba.size
    pixels = rgba.load()

    samples = []
    edge_margin_x = max(1, width // 12)
    edge_margin_y = max(1, height // 12)

    for y in range(height):
        for x in range(width):
            if x >= edge_margin_x and x < width - edge_margin_x and y >= edge_margin_y and y < height - edge_margin_y:
                continue
            r, g, b, a = pixels[x, y]
            if a > 0:
                samples.append((r, g, b, a))

    if not samples:
        samples = [pixels[x, y] for y in range(height) for x in range(width) if pixels[x, y][3] > 0]

    if not samples:
        return (0, 0, 0)

    total_alpha = sum(sample[3] for sample in samples)
    if total_alpha == 0:
        total_alpha = len(samples)

    red = sum(sample[0] * sample[3] for sample in samples) // total_alpha
    green = sum(sample[1] * sample[3] for sample in samples) // total_alpha
    blue = sum(sample[2] * sample[3] for sample in samples) // total_alpha
    return (red, green, blue)


def _resize_rgba_premultiplied(img, size, resample_method):
    """Resize using premultiplied alpha to prevent dark halos on transparent edges."""
    if img.mode != "RGBA":
        img = img.convert("RGBA")

    # Premultiply alpha before resize.
    pixels = img.load()
    premultiplied = []
    for y in range(img.size[1]):
        for x in range(img.size[0]):
            r, g, b, a = pixels[x, y]
            premultiplied.append((r * a // 255, g * a // 255, b * a // 255, a))

    premul_img = Image.new("RGBA", img.size)
    premul_img.putdata(premultiplied)
    resized = premul_img.resize(size, resample=resample_method)

    # Unpremultiply back to straight alpha for correct PNG output.
    resized_pixels = resized.load()
    unpremultiplied = []
    for y in range(size[1]):
        for x in range(size[0]):
            r, g, b, a = resized_pixels[x, y]
            if a == 0:
                unpremultiplied.append((0, 0, 0, 0))
                continue

            rr = min(255, (r * 255 + a // 2) // a)
            gg = min(255, (g * 255 + a // 2) // a)
            bb = min(255, (b * 255 + a // 2) // a)
            unpremultiplied.append((rr, gg, bb, a))

    out = Image.new("RGBA", size)
    out.putdata(unpremultiplied)
    return out


def _load_compression_cache(cache_path):
    if not os.path.exists(cache_path):
        return {}

    try:
        with open(cache_path, "r", encoding="utf-8") as handle:
            data = json.load(handle)
    except (OSError, json.JSONDecodeError):
        return {}

    if not isinstance(data, dict):
        return {}

    return data.get("files", {}) if isinstance(data.get("files", {}), dict) else {}


def _save_compression_cache(cache_path, cache_data):
    payload = {"files": cache_data}
    with open(cache_path, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=2, sort_keys=True)

def process_icons(input_dir, output_dir):
    if not os.path.exists(output_dir):
        os.makedirs(output_dir)

    cache_path = os.path.join(os.getcwd(), SOURCE_COMPRESSION_CACHE)
    compression_cache = _load_compression_cache(cache_path)

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
                img = img.convert("RGBA")

                # Source Compression: Check if file size > 200KB (204800 bytes)
                file_size = os.path.getsize(input_path)
                source_stat = os.stat(input_path)
                cache_entry = compression_cache.get(filename)
                already_compressed = (
                    cache_entry is not None
                    and cache_entry.get("size") == file_size
                    and cache_entry.get("mtime_ns") == source_stat.st_mtime_ns
                )

                if file_size > SOURCE_COMPRESSION_THRESHOLD and not already_compressed:
                    print(f"Compressing large source file: {filename} ({file_size/1024:.2f} KB)")
                    # Save back to input_path with optimization
                    img.save(input_path, optimize=True)

                    new_stat = os.stat(input_path)
                    compression_cache[filename] = {
                        "size": new_stat.st_size,
                        "mtime_ns": new_stat.st_mtime_ns,
                    }
                    compressed_count += 1
                elif already_compressed:
                    print(f"Source already compressed, skipping: {filename}")
                
                # Pre-processing: If 512x518, crop bottom 6 pixels
                if img.size == (512, 518):
                    img = img.crop((0, 0, 512, 512))
                    print(f"Pre-processed (cropped bottom): {filename}")

                # Remove transparent padding first to avoid interpolation artifacts.
                trimmed_img = _trim_transparent(img, alpha_threshold=TRIM_ALPHA_THRESHOLD)
                if trimmed_img.size != img.size:
                    print(f"Trimmed transparent border: {filename}")

                # Save the trimmed image directly so the output keeps only the visible content.
                trimmed_img.save(output_path, optimize=True)
                processed_count += 1
                print(f"Processed: {filename}")

        except Exception as e:
            print(f"Error processing {filename}: {e}")
            skipped_count += 1

    print(f"\nProcessing complete.")
    print(f"Total processed: {processed_count}")
    print(f"Source files compressed: {compressed_count}")
    print(f"Skipped/Errors: {skipped_count}")

    _save_compression_cache(cache_path, compression_cache)

if __name__ == "__main__":
    current_dir = os.getcwd()
    input_directory = os.path.join(current_dir, "PixelLauncherMods")
    output_directory = os.path.join(current_dir, "GlobalIconPack")
    
    process_icons(input_directory, output_directory)
