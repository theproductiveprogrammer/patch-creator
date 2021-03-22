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

3. Create patch from the diff of any commit/tag

   ```sh
   $> java PatchDiff
   ```

   ```
   ****************************************
   PatchDiff v1.0.0
   ****************************************
   Usage:
     1. Compile the script:
           $> javac PatchDiff.java
     2. Execute it providing the commit to start from:
           $> java PatchDiff <commit id>
        Eg.:
           $> java PatchDiff HEAD~3        <-- (3 commits back)
           $> java PatchDiff dbad1         <-- (specific commit)
           $> java PatchDiff rel-1.23      <-- (tag)
   ```

   

---

