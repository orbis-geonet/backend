# Resizer func #

<!-- toc -->

* [Description](#description)
* [Code overview](#code-overview)
* [Deployment overview](#deployment-overview)

<!-- tocstop -->

# Description

Cloud function for processing video and images making them of an appropriate size.

# Code overview

Code is located in src folder and uses TypeScript as a language.
- config.ts -- defines some service functions and defaults for the configuration parameters
- index.ts -- main entrypoint is located here, and it is called generateResizedImage it is triggered on storage onFinalize event
and downloads uploaded file to the temp folder, sets up parameters according to the configuration and delegates resizing to 
the image or video resize functions from resize-image.ts. If several formats and/or sizes of the file needs to be generated
resize function is called for each combo
- resize-image.ts -- main functionality lives here. `modifyImage` works with images and `modifyVideo` with video. Each of them prepares names and parameters for 
a particular resize to a target combination of format and filesize, sometimes trying to correct not entirely correct names, types, and sizes.
Then it uploads result to the same bucket with _WxH suffix turning some.jpg into e.g. some_200x200.jpg. After successful upload function writes key to RTDB
path `/images/image_name/200x200/generated` with value of `true`. Functions of `modifyVideo` are pretty much the same, but instead of `sharp` library it uses
`ffmpeg`
- utils.ts -- contains some code to normlize paths and names of the files

# Deployment overview

This sections assumes that you have installed
- gcloud
- relatively new nodejs and npm (works with node v16.13.2 and npm 8.1.2)
- you have necessary accesses to the console of the project in the gcloud we are removing for demo purposes

1. Build package with `npm run build`
2. Zip everything in resizerfunc folder `zip -r resizer.zip *`
3. Go to [Cloud console: Functions](https://console.cloud.google.com/functions/list) and select project for staging or production
4. Click on `resizer` - name of the function
5. Press Edit
6. If needed adjust env variable in `Runtime, build, connection and security settings` dropdown
7. Click Next
8. Select `ZIP Upload` from the dropdown on the left
9. Browse for a file created on step 2 and select `staging.<project-name>.appspot.com` as stage bucket
10. Press `Deploy`
11. Wait for cloud function to come back up
12. Verify deployment by uploading any image to default firebase bucket and checking if resized copies show up
