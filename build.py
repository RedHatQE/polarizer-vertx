from subprocess import PIPE, STDOUT, Popen
import os
import time
import argparse
from pathlib import Path

def launch(cmd: str, env=None, cwd=None, shell=False, timeout: int = 300):
    if env is None:
        env = os.environ
    if cwd is None:
        cwd = os.getcwd()
    if isinstance(cmd, str) and shell is False:
        cmd = cmd.split(" ")
        cmd = list(filter(lambda x: x != '', cmd))
    print("Executing command {}".format(cmd))

    proc = Popen(cmd, stdout=PIPE, stderr=STDOUT, env=env, cwd=cwd, shell=shell)
    out, err = proc.communicate()
    ret = proc.poll()
    if ret is not None and ret != 0:
        print("Command Failed. Return code = {}".format(ret))
    count = 0
    while ret is None and count < timeout:
        time.sleep(5)
        count += 5
        print("waiting for command '{}' to finish: {}".format(cmd, count))
    output = out.decode(encoding='utf-8')
    print(output)
    return output, proc.returncode

def build(path: str, opts):
    print("Cleaning, building and installing {}".format(path))
    launch("./gradlew clean", cwd=path, shell=True)
    launch("./gradlew install", cwd=path, shell=True)
    print("Upload = " + ",".join(opts.upload_archive_projects))
    if opts.upload_archive_projects:
        for i in opts.upload_archive_projects:
            print("i = {}, path = {}".format(i, path))
            if i in path:
                print("Uploading project {} to maven".format(i))
                if False:
                    launch("./gradlew uploadArchives", cwd=path, shell=True)

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--metadata-path", help="Path to the metadata project")
    parser.add_argument("--reporter-path", help="Path to the reporter project")
    parser.add_argument("--polarizer-umb-path", help="Path to the polarizer-umb project")
    parser.add_argument("--polarizer-path", help="Path to the polarizer project")
    parser.add_argument("-s", "--scp-jar", help="If True, upload the polarizer-vertx jar to auto-services",
                        action='store_true')
    parser.add_argument("--upload-archive-projects", help="Comma separated value of string projects", default="")
    opts = parser.parse_args()
    opts.upload_archive_projects = opts.upload_archive_projects.split(",")
    print("Projects to upload:")
    for i in opts.upload_archive_projects:
        print("\t" + i)

    # The projects should be built in this order
    paths = (opts.metadata_path, opts.reporter_path, opts.polarizer_umb_path, opts.polarizer_path)
    list(build(path, opts) for path in paths if path is not None)

    # Build polarizer-vertx itself
    build(".", opts)
    if opts.scp_jar:
        p = Path(os.getcwd()).joinpath("build/libs")
        fatjarpath = [str(x) for x in p.iterdir() if str(x).endswith("fat.jar")]
        if len(fatjarpath) != 1:
            raise Error("No fat jar")
        fatjar = fatjarpath[0]
        cmd = "scp {} root@auto-services.usersys.redhat.com:/var/www/html/polarizer".format(fatjar)
        launch(cmd, shell=True)

    # Remind to run playbook
    print("For production, make sure to run the playbook on the desired rhsm-cimetrics server")
    print("For example:")
    print('ansible-playbook -vv polarizer.yml --limit rhsm-cimetrics2 --extra-vars="polarizer_version=0.2.1-SNAPSHOT" --tags=polarizer')
