import re
import sys
import os

def update_version(gradle_file_path, tag_name):
    # Remove leading 'v' if present
    release_version = tag_name.lstrip('v')
    
    with open(gradle_file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    version_code_match = re.search(r'versionCode\s*=\s*(\d+)', content)
    version_name_match = re.search(r'versionName\s*=\s*"([^"]+)"', content)
    
    if not version_code_match or not version_name_match:
        print("Error: Could not find versionCode or versionName in build.gradle.kts")
        sys.exit(1)
        
    current_version_code = int(version_code_match.group(1))
    current_version_name = version_name_match.group(1)
    
    print(f"Current versionCode: {current_version_code}")
    print(f"Current versionName: {current_version_name}")
    print(f"Target release version: {release_version}")
    
    if current_version_name != release_version:
        new_version_code = current_version_code + 1
        new_version_name = release_version
        
        # Replace the versionCode and versionName
        new_content = re.sub(
            r'versionCode\s*=\s*\d+',
            f'versionCode = {new_version_code}',
            content,
            count=1
        )
        new_content = re.sub(
            r'versionName\s*=\s*"[^"]+"',
            f'versionName = "{new_version_name}"',
            new_content,
            count=1
        )
        
        with open(gradle_file_path, 'w', encoding='utf-8', newline='') as f:
            f.write(new_content)
            
        print(f"Updated: versionCode -> {new_version_code}, versionName -> {new_version_name}")
        
        if 'GITHUB_OUTPUT' in os.environ:
            with open(os.environ['GITHUB_OUTPUT'], 'a') as out:
                out.write("updated=true\n")
                out.write(f"version_code={new_version_code}\n")
                out.write(f"version_name={new_version_name}\n")
        else:
            print("GITHUB_OUTPUT not set, skipping writing output variables.")
    else:
        print("Version name already matches target release version. No changes needed.")
        if 'GITHUB_OUTPUT' in os.environ:
            with open(os.environ['GITHUB_OUTPUT'], 'a') as out:
                out.write("updated=false\n")

if __name__ == '__main__':
    if len(sys.argv) < 3:
        print("Usage: python update_version.py <gradle_file_path> <tag_name>")
        sys.exit(1)
    update_version(sys.argv[1], sys.argv[2])
