package org.bouncycastle.crypto.tls;

import java.util.Vector;

class DTLSReassembler {

    private final short msg_type;
    private final byte[] body;

    private Vector missing = new Vector();

    DTLSReassembler(short msg_type, int length) {
        this.msg_type = msg_type;
        this.body = new byte[length];

        if (length > 0) {
            this.missing.add(new Range(0, length));
        }
    }

    short getType()
    {
        return msg_type;
    }

    byte[] getBodyIfComplete()
    {
        return missing.isEmpty() ? body : null;
    }

    void contributeFragment(short msg_type, int length, byte[] buf, int off, int fragment_offset,
        int fragment_length) {

        int fragment_end = fragment_offset + fragment_length;

        if (this.msg_type != msg_type || this.body.length != length || fragment_end > length) {
            // TODO Throw exception?
        }

        if (fragment_length == 0)
            return;

        for (int i = 0; i < missing.size(); ++i) {
            Range range = (Range) missing.get(i);
            if (range.getStart() >= fragment_end)
                break;
            if (range.getEnd() > fragment_offset) {

                int copyStart = Math.max(range.getStart(), fragment_offset);
                int copyEnd = Math.min(range.getEnd(), fragment_end);
                int copyLength = copyEnd - copyStart;

                System.arraycopy(buf, off + copyStart - fragment_offset, body, copyStart,
                    copyLength);

                if (copyStart == range.getStart()) {
                    if (copyEnd == range.getEnd()) {
                        missing.removeElementAt(i--);
                    }
                    else {
                        range.setStart(copyEnd);
                    }
                }
                else {
                    if (copyEnd == range.getEnd()) {
                        range.setEnd(copyStart);
                    }
                    else {
                        missing.insertElementAt(new Range(copyEnd, range.getEnd()), ++i);
                        range.setEnd(copyStart);
                    }
                }
            }
        }
    }

    private static class Range {

        private int start, end;

        Range(int start, int end) {
            this.start = start;
            this.end = end;
        }

        public int getStart() {
            return start;
        }

        public void setStart(int start) {
            this.start = start;
        }

        public int getEnd() {
            return end;
        }

        public void setEnd(int end) {
            this.end = end;
        }
    }
}