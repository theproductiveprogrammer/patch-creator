# Patch Creator
Rough scripts to quickly create deployment bundles based on PR's or
commits/tags.

## Usage

1. Build class files:

   ```sh
   $> ./build.sh
   ```

   

2. Create patch from Bitbucket PR's

   ```sh
   $> ./PatchUp.sh
   ```

   

   ```
   ****************************************
   PatchUp v1.0.0
   ****************************************
   Usage:
     1. Create a file called '.patchup' that contains two lines:
           bitbucket_username
           bitbucket_app_password
        You can create an app password in your bitbucket profile.
        .patchup (SAMPLE):
           sbox_charles
           MYSECRETPASSWORD
     2. Call:
           ./PatchUp.sh <Pull Request Num> <Pull Request Num>...
   ```

