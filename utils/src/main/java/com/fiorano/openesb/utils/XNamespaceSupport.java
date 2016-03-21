/**
 * Copyright (c) 1999-2007, Fiorano Software Technologies Pvt. Ltd. and affiliates.
 * Copyright (c) 2008-2015, Fiorano Software Pte. Ltd. and affiliates.
 *
 * All rights reserved.
 *
 * This software is the confidential and proprietary information
 * of Fiorano Software ("Confidential Information").  You
 * shall not disclose such Confidential Information and shall use
 * it only in accordance with the terms of the license agreement
 * enclosed with this product or entered into with Fiorano.
 */





package com.fiorano.openesb.utils;

import org.xml.sax.helpers.NamespaceSupport;

import java.util.*;

/**
 * @author Santhosh Kumar T
 * Created On: Sep 24, 2004 1:27:04 PM
 */
public class XNamespaceSupport extends NamespaceSupport {
    private Hashtable suggestedPrefixes = null;   // <uri, prefix>

    public XNamespaceSupport(){
    }

    public XNamespaceSupport(Hashtable suggestedPrefixes){
        this.suggestedPrefixes = suggestedPrefixes;
    }

    public Hashtable getSuggestedPrefixes(){
        return suggestedPrefixes;
    }

    public void setSuggestedPrefixes(Hashtable suggestedPrefixes){
        this.suggestedPrefixes = suggestedPrefixes;
    }

    // declare a suitable prefix for the given uri if not already defined.
    public String declarePrefix(String uri){
        if(uri==null)
            return null;
        String prefix = findPrefix(uri);
        if(prefix!=null)
            return prefix;

        prefix = getSuggestedPrefix(uri);
        declarePrefix(prefix, uri);
        return prefix;
    }

    public void declarePrefixes(Properties/*<prefix, uri>*/ map){
        Iterator iter = map.entrySet().iterator();
        while(iter.hasNext()){
            Map.Entry entry = (Map.Entry)iter.next();
            declarePrefix((String)entry.getKey(), (String)entry.getValue());
        }
    }

    public String findPrefix(String uri){
        String defaultURI = getURI("");
        if(defaultURI==null)
            defaultURI = "";  //NOI18N
        if(uri.equals(defaultURI)) //NOI18N
            return "";   //NOI18N
        String prefix = getPrefix(uri);
        if(prefix!=null)
            return prefix;
        return null;
    }

    public String getSuggestedPrefix(String uri){
        Collection c = CollectionUtil.toCollection(getPrefixes());

        String prefix = (String)(suggestedPrefixes != null ? suggestedPrefixes.get(uri) : null);
        if(prefix==null)
            prefix = Namespaces.SUGGESTED.getPrefix(uri);
        if(prefix==null)
            prefix = suggestPrefix(uri);
        if(prefix!=null){
            if(!c.contains(prefix)){
                return prefix;
            }
        }else
            prefix = "ns";                                          //NOI18N

        int i = 0;
        while(c.contains(prefix+ ++i));
        return prefix+i;
    }

    // subclasses can dynamically suggest prefix by overrideing this method.
    // this is accounted only if it doesn't found it in suggestedPrefixes table.
    protected String suggestPrefix(String uri){
        return null;
    }

    public String getQName(String clarkName){
        String parts[] = ClarkName.getParts(clarkName);
        if(parts[0].length()==0)
            return clarkName;
        else{
            String prefix = getPrefix(parts[0]);
            return prefix!=null ? prefix+':'+parts[1] : parts[1];
        }
    }

    public String getQName(String uri, String localName){
        String prefix = getPrefix(uri);
        return prefix != null ? prefix + ':' + localName : localName;
    }

    public String getClarkName(String qName){
        int colon = qName.indexOf(':');
        String prefix = colon != -1 ? qName.substring(0, colon) : ""; //NOI18N
        String localName = colon != -1 ? qName.substring(colon + 1) : qName;
        return ClarkName.toClarkName(getURI(prefix), localName);
    }
}