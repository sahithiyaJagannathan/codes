#!/bin/bash

buildPath="$1"
md5sumcheckVar="chumma"

fswatch --recursive --exclude=".*sw[px]" --event Updated "$buildPath/product_package" "$buildPath/webapps" "$buildPath/source" | while read -r line
do
    sourceFilePath="$line"
    sourceFilePath=$(echo "$sourceFilePath" | sed 's/~//')  # Remove tilde if present

    md5sumoutput=$(md5 -q "$sourceFilePath")

    isFileModified=$(echo "$md5sumoutput" | grep "$md5sumcheckVar")

    if [ -z "$isFileModified" ]; then
        md5sumcheckVar="$md5sumoutput"
        sourceFileName=$(basename "$sourceFilePath")
        isNotJavaFile=$(echo "$sourceFileName" | grep '\.java$')

        if [ -z "$isNotJavaFile" ]; then
            cd "$buildPath/build/ZOHODB/output/AdventNet/Sas/tomcat/webapps/ROOT"

            totalFileCount=$(find . -name "$sourceFileName" | wc -l)
            find . -name "$sourceFileName"

            if [ "$totalFileCount" -gt 1 ]; then
                echo "$buildPath"
                file_path=$(echo "$sourceFilePath" | sed "s:$buildPath/webapps/db/::" | sed "s:$sourceFileName::")
                echo $sourceFilePath
                cd "$file_path"
                echo "Moving file from $sourceFilePath to $(pwd)/$sourceFileName"
                cp "$sourceFilePath" ./
            else
                destinationFilePath=$(find . -name "$sourceFileName")

                if [ -z "$destinationFilePath" ]; then
                    echo "\n$(date) ==> File '$sourceFileName' is not found in deployment directories."
                else
                    cp "$sourceFilePath" "$destinationFilePath"
                    echo "\n$(date) ==> File '$sourceFilePath' is copied to '$buildPath/build/ZOHODB/output/AdventNet/Sas/tomcat/webapps/ROOT$destinationFilePath'"

                    isScssFile=$(echo "$sourceFileName" | grep '\.scss$')
                    if [ "$isScssFile" ]; then
                        cd "$buildPath/build/ZOHODB/output/AdventNet/Sas/tomcat/webapps/ROOT/themes/common/styles/"
                        echo "$sourceFileName" | bash ScssToCss.sh
                    fi
                fi
            fi
        else
            cd "$buildPath/build/ZOHODB/output/AdventNet/Sas/tomcat/webapps/ROOT/WEB-INF/lib"
            export CLASSPATH="$buildPath/build/ZOHODB/output/AdventNet/Sas/tomcat/webapps/ROOT/WEB-INF/lib/*:$buildPath/build/ZOHODB/output/AdventNet/Sas/tomcat/lib/*"
            echo "\n$(date) ==> Compiling $sourceFileName File"
            javac -d . "$sourceFilePath"
            out=$?
            if [ $out -eq 0 ]; then
                echo "$(date) ==> Updating AdventNetZohoDB.jar File"
                jar uf AdventNetZohoDB.jar com
                if [ $? -eq 0 ]; then
                    echo "$(date) ==> AdventNetZohoDB.jar Updated Successfully..."
                fi
                rm -rf com
            fi
        fi
    fi
done

