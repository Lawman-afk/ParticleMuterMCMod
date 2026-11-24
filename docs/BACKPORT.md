# The 1.20.1 port

This branch is made from the 1.21.1 branch by `tools/backport/port.py`: it reads each of that branch's files at the commit in
`tools/backport/base.txt`, renames what 1.20.1 names otherwise (RULES, IMPORTS) and changes what it does otherwise
(FILE_FIXES), and writes the result here. Files listed in `tools/backport/manual.txt` are this branch's own, edited by
hand; the build files are too.

To bring the branch up to a new commit of the 1.21.1 branch:

    python tools/backport/port.py --sync $(git rev-parse 1.21.1)

then compile, run the Lab (`./gradlew runLab`, its `Lab check:` lines in the log), and commit.
