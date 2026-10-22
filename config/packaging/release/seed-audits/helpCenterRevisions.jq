[.[] | select(.documentType=="privacy" and .active==true)] as $documents
    | ($documents|length)==2 and ([$documents[].lang]|sort)==["en","hu"]
    and all($documents[]; (.sections|length)>0)
