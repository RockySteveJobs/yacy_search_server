// Request.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 14.03.2007 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// LICENSE
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package de.anomic.crawler.retrieval;

import java.io.IOException;
import java.util.Date;

import net.yacy.cora.document.UTF8;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.order.Base64Order;
import net.yacy.kelondro.order.Bitfield;
import net.yacy.kelondro.order.NaturalOrder;
import net.yacy.kelondro.workflow.WorkflowJob;

public class Request extends WorkflowJob
{

    // row definition for balancer-related NURL-entries
    public final static Row rowdef = new Row("String urlhash-" + Word.commonHashLength + ", " + // the url's hash
        "String initiator-"
        + Word.commonHashLength
        + ", "
        + // the crawling initiator
        "String urlstring-256, "
        + // the url as string
        "String refhash-"
        + Word.commonHashLength
        + ", "
        + // the url's referrer hash
        "String urlname-80, "
        + // the name of the url, from anchor tag <a>name</a>
        "Cardinal appdate-8 {b256}, "
        + // the date of the resource; either file date or first appearance
        "String profile-"
        + Word.commonHashLength
        + ", "
        + // the name of the prefetch profile handle
        "Cardinal depth-2 {b256}, "
        + // the prefetch depth so far, starts at 0
        "Cardinal parentbr-3 {b256}, "
        + // number of anchors of the parent
        "Cardinal forkfactor-4 {b256}, "
        + // sum of anchors of all ancestors
        "byte[] flags-4, "
        + // flags
        "Cardinal handle-4 {b256}, "
        + // handle (NOT USED)
        "Cardinal loaddate-8 {b256}, "
        + // NOT USED
        "Cardinal lastmodified-8 {b256}, "
        + // NOT USED
        "Cardinal size-8 {b256}", // size of resource in bytes (if known) or 0 if not known
        Base64Order.enhancedCoder);

    private byte[] initiator; // the initiator hash, is NULL or "" if it is the own proxy;
                              // if this is generated by a crawl, the own peer hash in entered
    private byte[] refhash; // the url's referrer hash
    private DigestURI url; // the url as string
    private String name; // the name of the url, from anchor tag <a>name</a>
    private long appdate; // the time when the url was first time appeared.
    private String profileHandle; // the name of the fetch profile
    private int depth; // the prefetch depth so far, starts at 0
    private int anchors; // number of anchors of the parent
    private int forkfactor; // sum of anchors of all ancestors
    private Bitfield flags;
    private long size; // size of resource in bytes (if known) or 0 if not known
    private String statusMessage;
    private int initialHash; // to provide a object hash that does not change even if the url changes because of redirection

    /**
     * convenience method for 'full' request object
     *
     * @param url
     * @param referrerhash
     */
    public Request(final DigestURI url, final byte[] referrerhash) {
        this(null, url, referrerhash, null, null, null, 0, 0, 0, 0);
    }

    /**
     * A Request Entry is a object that is created to provide all information to load a specific resource.
     *
     * @param initiator the hash of the initiator peer
     * @param url the {@link URL} to crawl
     * @param referrer the hash of the referrer URL
     * @param name the name of the document to crawl
     * @param appdate the time when the url was first time appeared
     * @param profileHandle the name of the prefetch profile. This must not be null!
     * @param depth the crawling depth of the entry
     * @param anchors number of anchors of the parent
     * @param forkfactor sum of anchors of all ancestors
     */
    public Request(
        final byte[] initiator,
        final DigestURI url,
        final byte[] referrerhash,
        final String name,
        final Date appdate,
        final String profileHandle,
        final int depth,
        final int anchors,
        final int forkfactor,
        final long size) {
        // create new entry and store it into database
        assert url != null;
        assert profileHandle == null || profileHandle.length() == Word.commonHashLength : profileHandle
            + " != "
            + Word.commonHashLength;
        url.removeRef(); // remove anchor reference
        this.initiator = (initiator == null) ? null : ((initiator.length == 0) ? null : initiator);
        this.url = url;
        this.refhash = referrerhash;
        this.name = (name == null) ? "" : name;
        this.appdate = (appdate == null) ? 0 : appdate.getTime();
        this.profileHandle = profileHandle; // must not be null
        this.depth = depth;
        this.anchors = anchors;
        this.forkfactor = forkfactor;
        this.flags = new Bitfield(rowdef.width(10));
        this.statusMessage = "loaded(args)";
        this.initialHash = url.hashCode();
        this.status = WorkflowJob.STATUS_INITIATED;
        this.size = size;
    }

    public Request(final Row.Entry entry) throws IOException {
        assert (entry != null);
        insertEntry(entry);
    }

    private void insertEntry(final Row.Entry entry) throws IOException {
        try {
            final String urlstring = entry.getColUTF8(2);
            if ( urlstring == null ) {
                throw new IOException("url string is null");
            }
            this.initiator = entry.getColBytes(1, true);
            this.initiator =
                (this.initiator == null) ? null : ((this.initiator.length == 0) ? null : this.initiator);
            this.url = new DigestURI(urlstring, entry.getPrimaryKeyBytes());
            this.refhash = (entry.empty(3)) ? null : entry.getColBytes(3, true);
            this.name = (entry.empty(4)) ? "" : entry.getColUTF8(4).trim();
            this.appdate = entry.getColLong(5);
            this.profileHandle = (entry.empty(6)) ? null : entry.getColASCII(6).trim();
            this.depth = (int) entry.getColLong(7);
            this.anchors = (int) entry.getColLong(8);
            this.forkfactor = (int) entry.getColLong(9);
            this.flags = new Bitfield(entry.getColBytes(10, true));
            //this.loaddate = entry.getColLong(12);
            //this.lastmodified = entry.getColLong(13);
            this.size = entry.getColLong(14);
            this.statusMessage = "loaded(kelondroRow.Entry)";
            this.initialHash = this.url.hashCode();
        } catch ( Throwable e ) {
            throw new IOException(e.getMessage());
        }
        return;
    }

    @Override
    public int hashCode() {
        // overloads Object.hashCode()
        return this.initialHash;
    }

    public void setStatus(final String s, final int code) {
        //System.out.println("***DEBUG*** crawler status " + s + ", " + code + " for " + this.url.toNormalform(true, false));
        this.statusMessage = s;
        this.status = code;
    }

    public String getStatus() {
        return this.statusMessage;
    }

    public Row.Entry toRow() {
        final byte[] appdatestr = NaturalOrder.encodeLong(this.appdate, rowdef.width(5));
        final byte[] loaddatestr = NaturalOrder.encodeLong(0 /*loaddate*/, rowdef.width(12));
        final byte[] serverdatestr = NaturalOrder.encodeLong(0 /*lastmodified*/, rowdef.width(13));
        final byte[] sizestr = NaturalOrder.encodeLong(this.size, rowdef.width(14));
        // store the hash in the hash cache
        final byte[] namebytes = UTF8.getBytes(this.name);
        final byte[][] entry =
            new byte[][] {
                this.url.hash(),
                this.initiator,
                this.url.toString().getBytes(),
                this.refhash,
                namebytes,
                appdatestr,
                (this.profileHandle == null) ? null : this.profileHandle.getBytes(),
                NaturalOrder.encodeLong(this.depth, rowdef.width(7)),
                NaturalOrder.encodeLong(this.anchors, rowdef.width(8)),
                NaturalOrder.encodeLong(this.forkfactor, rowdef.width(9)),
                this.flags.bytes(),
                NaturalOrder.encodeLong(0, rowdef.width(11)),
                loaddatestr,
                serverdatestr,
                sizestr
            };
        return rowdef.newEntry(entry);
    }

    public DigestURI url() {
        // the url
        return this.url;
    }

    public void redirectURL(final DigestURI redirectedURL) {
        // replace old URL by new one. This should only be used in case of url redirection
        this.url = redirectedURL;
    }

    public byte[] referrerhash() {
        // the urlhash of a referer url
        return this.refhash;
    }

    public byte[] initiator() {
        // returns the hash of the initiating peer
        return this.initiator;
    }

    public boolean proxy() {
        // true when the url was retrieved using the proxy
        return (initiator() == null || initiator().length == 0);
    }

    public Date appdate() {
        // the date when the url appeared first
        return new Date(this.appdate);
    }

    /*
    public Date loaddate() {
        // the date when the url was loaded
        return new Date(this.loaddate);
    }

    public Date lastmodified() {
        // the date that the server returned as document date
        return new Date(this.lastmodified);
    }
    */
    public long size() {
        // the date that the client (browser) send as ifModifiedSince in proxy mode
        return this.size;
    }

    public String name() {
        // return the anchor name (text inside <a> tag)
        return this.name;
    }

    public int depth() {
        // crawl depth where the url appeared
        return this.depth;
    }

    public String profileHandle() {
        // the handle of the crawl profile
        assert this.profileHandle.length() == Word.commonHashLength : this.profileHandle
            + " != "
            + Word.commonHashLength;
        return this.profileHandle;
    }

}