package com.dell.gumshoe.inspector.tools;

import com.dell.gumshoe.inspector.FileDataParser;
import com.dell.gumshoe.inspector.SampleSource;
import com.dell.gumshoe.stack.Stack;
import com.dell.gumshoe.stats.StatisticAdder;

import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

import java.awt.Component;
import java.awt.HeadlessException;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class SampleFileChooser extends JFileChooser implements SampleSource {
    private static final SimpleDateFormat hms = new SimpleDateFormat("HH:mm:ss");

    private final List<SampleSelectionListener> listeners = new CopyOnWriteArrayList<>();

    private JDialog dialog;
    private FileDataParser parser;
    private int locationX, locationY;
    public SampleFileChooser() {
        setApproveButtonText("Parse");
    }

    protected JDialog createDialog(Component parent) throws HeadlessException {
        dialog = super.createDialog(parent);
        dialog.setLocation(locationX, locationY);
        return dialog;
    }

    public void setLocation(int x, int y) {
        if(dialog!=null) {
            dialog.setLocation(x, y);
        }
        locationX = x;
        locationY = y;
    }

    public void approveSelection() {
        closeParser();
        openParser(getSelectedFile());
        notifyContentsChanged();
        super.approveSelection();
    }

    private void notifyError(String message) {
        JOptionPane.showMessageDialog(this, message, "File Open Failed!", JOptionPane.ERROR_MESSAGE);
    }

    private void closeParser() {
        if(parser!=null) {
            try { parser.close(); }
            catch(Exception ignore) { }
        }

        parser = null;
    }

    private void openParser(File file) {
        if( ! validFile(file)) { return; }

        try {
            parser = new FileDataParser(file);
        } catch(Exception ex) {
            ex.printStackTrace();
            notifyError("Error opening file: " + ex.getMessage());
        }

        try {
            readSample(true);
        } catch(Exception ex) {
            ex.printStackTrace();
            notifyError("Error parsing contents: " + ex.getMessage());
        }
    }

    private boolean validFile(File file) {
        if( ! file.exists()) {
            notifyError("Not found: " + file);
            return false;
        } else if( ! file.isFile()) {
            notifyError("Not a file: " + file);
            return false;
        } else if( ! file.canRead()) {
            notifyError("No read permission for: " + file);
            return false;
        }
        return true;
    }

    private void readSample(boolean forward) {
        new FileOpener(forward).execute();
    }

    public void addListener(SampleSelectionListener listener) {
        listeners.add(listener);
    }

    private void relayStats(String file, String time, String type, Map<Stack,StatisticAdder> data) {
        for(SampleSelectionListener listener : listeners) {
            listener.sampleWasSelected(file, time, type, data);
        }
    }

    private class FileOpener extends SwingWorker<Map<Stack,StatisticAdder>,Object> {
        private final boolean forward;
        public FileOpener(boolean forward) {
            this.forward = forward;
        }
        @Override
        public Map<Stack,StatisticAdder> doInBackground() throws Exception {
            return forward ? parser.getNextSample() : parser.getPreviousSample();
        }
        @Override
        public void done() {
            try {
                final Map<Stack,StatisticAdder> sample = get();
                if(sample!=null) {
                    final Date time = parser.getSampleTime();
                    final String sampleTime = hms.format(time);
                    relayStats(parser.getFilename(), parser.getSampleType(), sampleTime, sample);
                }
            } catch(Exception ex) {
                notifyError("Parse error reading file");
            }
        }
    }

    /////

    public void nextSample() {
        readSample(true);
    }

    public void previousSample() {
        readSample(false);
    }

    @Override
    public boolean hasNext() {
        return parser.hasNext();
    }

    @Override
    public boolean hasPrevious() {
        return parser.hasPrevious();
    }

    private void notifyContentsChanged() {
        for(SampleSelectionListener listener : listeners) {
            listener.contentsChanged(this);
        }
    }
}
