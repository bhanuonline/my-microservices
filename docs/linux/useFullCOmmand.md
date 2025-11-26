
how create a file : nano <fileName>

How to compare two file colordiff -y file1.txt file2.txt

     | = line differs between files.
     < = line only in left file.
     > = line only in right file.

    use grep grep -Fxi -f file1.txt file2.txt
        -i → ignore case
        -v → invert match (show lines NOT matching)
        -n → show line numbers
        -c → count how many matches
        -w → match whole words only
        -x	Match whole lines only
        -F	Fixed string search
        -f	Read patterns from another file

        -F means Fixed strings mode — don’t treat the pattern as a regular expression; match the exact text.
        -x means match the whole line. prtialmatch 123abc123 , abc match here 
        -i means ignore case.
        -f <file> means:Instead of typing the search patterns on the command line, read them from this file — one pattern per line.
