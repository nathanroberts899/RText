/*
 * 03/19/2004
 *
 * AbstractMainView.java - Abstract class representing a collection of
 * RTextEditorPanes.  This class contains all logic that would be common to
 * different implementations (i.e., everything except the view parts).
 * Copyright (C) 2004 Robert Futrell
 * robert_futrell at users.sourceforge.net
 * http://rtext.fifesoft.com
 *
 * This file is a part of RText.
 *
 * RText is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or any later version.
 *
 * RText is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.fife.rtext;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.Timer;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.EventListenerList;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Caret;

import org.fife.io.UnicodeWriter;
import org.fife.rtext.lang.LanguageSupport;
import org.fife.rtext.lang.LanguageSupportFactory;
import org.fife.ui.UIUtil;
//import org.fife.ui.autocomplete.*;
import org.fife.ui.app.StandardAction;
import org.fife.ui.rsyntaxtextarea.ErrorStrip;
import org.fife.ui.rsyntaxtextarea.FileLocation;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rsyntaxtextarea.parser.ParserNotice;
import org.fife.ui.rtextarea.Gutter;
import org.fife.ui.rtextarea.IconGroup;
import org.fife.ui.rtextarea.Macro;
import org.fife.ui.rtextarea.RTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.fife.ui.rtextfilechooser.RTextFileChooser;
import org.fife.ui.search.*;


/**
 * Abstract class representing a collection of RTextEditorPanes.  This class
 * contains all logic that would be common to different implementations (i.e.,
 * everything except the "view" parts).<p>
 *
 * An implementation of this class must fire a property change event of type
 * {@link #CURRENT_DOCUMENT_PROPERTY} whenever the currently-active document
 * changes so that other pieces of RText can function properly.<p>
 *
 * RText plugins may wish to register to be
 * <code>CurrentTextAreaListener</code>s if they want to be notified whenever
 * a property of the currently-active text area (or the text area itself)
 * changes.
 *
 * @author Robert Futrell
 * @version 0.5
 */
public abstract class AbstractMainView extends JPanel
		implements PropertyChangeListener, ActionListener,
				FindInFilesListener, HyperlinkListener {

	public static final int DOCUMENT_SELECT_TOP		= JTabbedPane.TOP;
	public static final int DOCUMENT_SELECT_LEFT		= JTabbedPane.LEFT;
	public static final int DOCUMENT_SELECT_BOTTOM	= JTabbedPane.BOTTOM;
	public static final int DOCUMENT_SELECT_RIGHT	= JTabbedPane.RIGHT;

	// Actions owned by the main view.
	public static final int FIND_ACTION			= 0;
	public static final int FIND_NEXT_ACTION		= 1;
	public static final int REPLACE_ACTION			= 2;
	public static final int REPLACE_NEXT_ACTION		= 3;
	public static final int REPLACE_ALL_ACTION		= 4;
	public static final int FIND_IN_FILES_ACTION		= 5;
	public static final int REPLACE_IN_FILES_ACTION	= 6;
	public static final int PRINT_ACTION			= 7;
	public static final int PRINT_PREVIEW_ACTION		= 8;
	public static final int CLOSE_ACTION			= 9;
	public static final int CLOSE_ALL_ACTION		= 10;
	public static final int GOTO_ACTION			= 11;
	public static final int LTR_ACTION				= 12;
	public static final int RTL_ACTION				= 13;
	public static final int VIEW_SPLIT_HORIZ_ACTION	= 14;
	public static final int VIEW_SPLIT_NONE_ACTION	= 15;
	public static final int VIEW_SPLIT_VERT_ACTION	= 16;
	public static final int NUM_ACTIONS			= 17;

	public static final String CURRENT_DOCUMENT_PROPERTY		= "MainView.currentDocument";
	public static final String DEFAULT_ENCODING_PROPERTY		= "MainView.defaultEncoding";
	public static final String FILE_SIZE_CHECK_PROPERTY		= "MainView.fileSizeCheck";
	public static final String FRACTIONAL_METRICS_PROPERTY		= "MainView.fractionalMetrics";
	public static final String MARK_ALL_COLOR_PROPERTY		= "MainView.markAllColor";
	public static final String MARK_OCCURRENCES_COLOR_PROPERTY	= "MainView.markOccurrencesColor";
	public static final String MARK_OCCURRENCES_PROPERTY		= "MainView.markOccurrences";
	public static final String MAX_FILE_SIZE_PROPERTY			= "MainView.maxFileSize";
	public static final String ROUNDED_SELECTION_PROPERTY		= "MainView.roundedSelection";
	public static final String SMOOTH_TEXT_PROPERTY			= "MainView.smoothText";
	public static final String TEXT_AREA_ADDED_PROPERTY		= "MainView.textAreaAdded";
	public static final String TEXT_AREA_REMOVED_PROPERTY	= "MainView.textAreaRemoved";

	private RTextEditorPane currentTextArea;			// Currently active text area.

	private Action[] actions;

	public FindDialog findDialog;					// The dialog that lets you search for text.
	public ReplaceDialog replaceDialog;			// The dialog that lets you replace text.
	public Vector searchStrings;					// The strings to go in the "Find What" combo boxes.
	public boolean searchingForward;				// Whether to search forward.
	public boolean searchMatchCase;				// If true, match case while searching.
	public boolean searchWholeWord;				// Whether searches look for spaces around matches.
	public boolean searchRegExpression;			// If true, searchString is a Java regular expression.
	public boolean searchMarkAll;					// If true, all search results will be marked.
	private boolean lineNumbersEnabled;			// If true, line numbers are visible on the documents.
	private boolean lineWrapEnabled;				// If true, word wrap is enabled for all documents.
	private String defaultLineTerminator;			// Line terminator of new text files.
	private String defaultEncoding;				// Encoding of new text files.
	private boolean guessFileContentType;

	public FindInFilesDialog findInFilesDialog;		// Dialog for searching for text in files.
	public ReplaceInFilesDialog replaceInFilesDialog;
	public GoToDialog goToDialog;					// Dialog that lets you go to a certain line number.

	private int textMode;						// Either INSERT_MODE or OVERWRITE_MODE.
	private int tabSize;						// The size (in spaces) tabs are.
	private boolean emulateTabsWithWhitespace;		// If true, tabs are emulated with spaces.

	private Font printFont;						// The font to use when printing a document.
	private Color caretColor;					// The color used for carets.
	private Color selectionColor;					// The color used for selections.

	private Object backgroundObject;				// Object used to draw text areas' backgrounds.
	private float imageAlpha;					// Alpha value used to make the bg image translucent.
	private String backgroundImageFileName;			// Background image, or null if background is a color.

	protected RText owner;						// The owner of this tabbed panel.

	private SyntaxFilters syntaxFilters;			// Used to decide how to syntax highlight a file.

	private boolean highlightCurrentLine;			// Whether or not the current line is highlighted.
	private Color currentLineColor;				// The color with which to highlight the current line.

	private boolean highlightModifiedDocDisplayNames;	// Color display names of modified files differently?
	private Color modifiedDocumentDisplayNameColor;	// Color to color display names of modified editors.

	private boolean checkForModification;			// Check for files being changed outside of RText?
	private long modificationCheckDelay = 10000;		// Delay in milliseconds.

	private boolean bracketMatchingEnabled;
	private Color matchedBracketBGColor;
	private Color matchedBracketBorderColor;

	private boolean marginLineEnabled;
	private int marginLinePosition;
	private Color marginLineColor;

	private boolean hyperlinksEnabled;
	private Color hyperlinkColor;
	private int hyperlinkModifierKey;

	private boolean whitespaceVisible;			// Whether whitespace is visible in the text areas.

	private String aaHintFieldName;			// Whether text is anti-aliased.
	private boolean fractionalMetricsEnabled;	// Whether fractional fontmetrics are used.

	private Color markAllHighlightColor;

	private boolean markOccurrences;
	private Color markOccurrencesColor;

	private boolean roundedSelectionEdges;
	private int caretBlinkRate;

	private int[] carets;					// index 0=>insert, 1=>overwrite.

	private boolean doFileSizeCheck;
	private float maxFileSize;				// In MB.

	private Font textAreaFont;
	private boolean textAreaUnderline;
	private Color textAreaForeground;
	private ComponentOrientation textAreaOrientation;

	private EventListenerList listenerList;

	private Icon bookmarkIcon;
	private boolean bookmarksEnabled;
	private Font lineNumberFont;
	private Color lineNumberColor;
	private Color gutterBorderColor;

	private SpellingSupport spellingSupport;

	/**
	 * The cursor used when recording a macro.
	 */
	private static Cursor macroCursor;


	/**
	 * Constructor.<p>
	 * You should call {@link #initialize} right after this.
	 */
	public AbstractMainView() {

		listenerList = new EventListenerList();

		ClassLoader cl = getClass().getClassLoader();
		URL url = cl.getResource("org/fife/rtext/graphics/bookmark.png");
		if (url!=null) {
			bookmarkIcon = new ImageIcon(url);
		}

		checkForModification = true;
		Timer t = new Timer();
		t.schedule(new TimerTask() {
				public void run() {
					checkFilesForOutsideModification();
				}
			},
			modificationCheckDelay,	// Initial delay.
			modificationCheckDelay);	// Check for files modified outside
								// of the editor every 30 seconds.

	}


	// Callback for various actions.
	public void actionPerformed(ActionEvent e) {

		String command = e.getActionCommand();

		// If they click the "Find" button on the Find/Replace dialog...
		if (command.equals("FindNext")) {
			getAction(FIND_NEXT_ACTION).actionPerformed(null);
		}

		// If they click on the "Replace" button on the Replace dialog...
		else if (command.equals("Replace")) {
			getAction(REPLACE_NEXT_ACTION).actionPerformed(null);
		}

		// If they clicked on the "Replace All" button on the Replace dialog...
		else if (command.equals("ReplaceAll")) {
			getAction(REPLACE_ALL_ACTION).actionPerformed(null);
		}

		// If a file was found to be modified outside of the editor...
		else if (command.startsWith("FileModified. ")) {
			handleFileModifiedEvent(command);
		}

	}


	/**
	 * Adds a current text area listener.
	 *
	 * @param l The listener to add.
	 * @see #removeCurrentTextAreaListener
	 */
	public void addCurrentTextAreaListener(CurrentTextAreaListener l) {
		listenerList.add(CurrentTextAreaListener.class, l);
	}


	/**
	 * Adds an empty text file to this tabbed pane.  This method is
	 * synchronized so it doesn't interfere with the thread checking for
	 * files being modified outside of the editor.
	 *
	 * @param fileNameAndPath The full path and name of the file to add.
	 * @param encoding The encoding in which the file is to be saved.  If
	 *        an invalid value is passed in, the system default encoding
	 *        is used.
	 */
	private synchronized void addNewEmptyFile(String fileNameAndPath,
												String encoding) {

		// Ensure the encoding is a proper value.
		if (encoding==null) {
			encoding = RTextFileChooser.getDefaultEncoding();
		}

		// Actually create the file on disk.
		if (!fileNameAndPath.equals(getDefaultFileName())) {
			try {
				new File(fileNameAndPath).createNewFile();
			} catch (IOException ioe) {
				String text = owner.getString("ErrorWritingFile",
								fileNameAndPath, ioe.getMessage());
				JOptionPane.showMessageDialog(this, text,
								owner.getString("ErrorDialogTitle"),
								JOptionPane.ERROR_MESSAGE);
			}
		}

		// Set pointers for easy reference to new document.
		try {
			currentTextArea = createRTextEditorPane(fileNameAndPath,
										encoding, null);
		} catch (IOException ioe) {
			owner.displayException(ioe);
			ensureFilesAreOpened();
			return;
		}

		// Add new text file to tabbed pane.
		RTextScrollPane scrollPane = createScrollPane(currentTextArea);
		currentTextArea.applyComponentOrientation(getTextAreaOrientation());
		addTextAreaImpl(currentTextArea.getFileName(), scrollPane,
								currentTextArea.getFileFullPath());

		// Let anybody who cares know we've opened this file.
		firePropertyChange(TEXT_AREA_ADDED_PROPERTY, null, currentTextArea);

	}


	/**
	 * Adds an empty text file with a default name to this panel.  This method
	 * is synchronized so it doesn't interfere with the thread checking for
	 * files being modified outside of the editor.
	 */
	public synchronized void addNewEmptyUntitledFile() {
		addNewEmptyFile(getDefaultFileName(), getDefaultEncoding());
	}


	/**
	 * Adds a text area to this view.  This method fires a property change
	 * event of type {@link #OLD_FILE_ADDED_PROPERTY}.
	 *
	 * @param textArea The text area to add.
	 * @see #addTextAreaImpl(String, Component, String)
	 */
	private void addTextArea(RTextEditorPane textArea) {

		// This is needed because the text area's undoManager picked up
		// the read() call above and added it as an insertion edit.  We
		// don't want the user to be able to undo this, however.
		textArea.discardAllEdits();

		// Add the new document into our tabbed pane.
		// This sets currentTextArea==tempTextArea.
		RTextScrollPane scrollPane = createScrollPane(textArea);
		textArea.applyComponentOrientation(getTextAreaOrientation());
		addTextAreaImpl(textArea.getFileName(), scrollPane,
						textArea.getFileFullPath());

		// REMEMBER: currentTextArea has just been updated by
		// addTextAreaImpl() above!!

		// Let anybody who cares know we've opened this file.
		firePropertyChange(TEXT_AREA_ADDED_PROPERTY, null, currentTextArea);
		moveToTopOfCurrentDocument();

	}


	/**
	 * Adds a text area visually to this panel.
	 *
	 * @param title The name of the document to display.
	 * @param component The component to add (usually an RTextScrollPane).
	 * @param fileFullPath The full path to the file being displayed by the
	 *        component.
	 */
	protected abstract void addTextAreaImpl(String title,
							Component component, String fileFullPath);


	/**
	 * Overridden so we ensure text areas keep their special LTR or RTL
	 * orientaitons.
	 *
	 * @param o The new component orientation.
	 */
	public void applyComponentOrientation(ComponentOrientation o) {
		super.applyComponentOrientation(o);
		// Force a reset of textAreaOrientation since the
		// applyComponentOrientation() above will trickle down to the
		// text areas and override their special orientations.
		ComponentOrientation temp = getTextAreaOrientation();
		textAreaOrientation = null;
		setTextAreaOrientation(temp);
	}


	/**
	 * Returns whether or not tabs are emulated with spaces.
	 *
	 * @return <code>true</code> iff tabs are emulated with spaces.
	 */
	public boolean areTabsEmulated() {
		return emulateTabsWithWhitespace;
	}


	/**
	 * Checks the "modified" timestamps for open files against the last known
	 * "modified" timestamps to see if any files have been modified outside of
	 * this RText instance.  This method is synchronized so that it isn't
	 * called while the user is loading or saving a file.
	 */
	public synchronized void checkFilesForOutsideModification() {

		// If we're currently not waiting on the user to decide about a
		// previous "another program modified..." message...
		if (checkForModification==true) {

			// Flag so that if the user takes to long deciding, messages
			// don't pile up about the same file being modified.
			// NOTE:  This is theoretically not thread-safe, but the
			// delay is set at 10 seconds, so it should be more than
			// enough to get to and complete this line).
			checkForModification = false;

			int numDocuments = getNumDocuments();
			StringBuffer sb = new StringBuffer();
			for (int i=0; i<numDocuments; i++) {
				RTextEditorPane textArea = getRTextEditorPaneAt(i);
				if (textArea.isModifiedOutsideEditor()) {
					sb.append(' ').append(i);
				}
			}

			// If no documents were modified outside the editor, allow the
			// thread to check again; otherwise, remember to prompt the user
			// about all of the documents that changed outside of the editor.
			if (sb.length()==0) {
				checkForModification = true;
			}
			else {
				final String actionCommand = "FileModified." + sb.toString();
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						actionPerformed(new ActionEvent(this,
							ActionEvent.ACTION_PERFORMED, actionCommand));
					}});
			}


		} // End of if (checkForModification==true).
	}


	/**
	 * Attempts to close all currently active documents.
	 *
	 * @return <code>true</code> if all active documents were closed, and
	 *         <code>false</code> if they weren't (i.e., the user hit cancel).
	 */
	public boolean closeAllDocuments() {
		return closeAllDocumentsExcept(-1);
	}


	/**
	 * Attempts to close all currently active documents except the one
	 * specified.
	 *
	 * @return <code>true</code> if the documents were all closed, and
	 *         <code>false</code> if they weren't (i.e., the user hit cancel).
	 */
	public boolean closeAllDocumentsExcept(int except) {

		int numDocuments = getNumDocuments();
		setSelectedIndex(numDocuments-1); // Start at the back.

		// Cycle through each document, one by one.
		for (int i=numDocuments-1; i>=0; i--) {

			if (i==except) {
				// Instead of removing this document, set focus to the
				// "first" document, and continue closing documents with the
				// next iteration.  Since we're only keeping around 1
				// document, this keeps it open.
				if (i>0) {
					setSelectedIndex(0);
				}
			}
			else {

				// Try to close the document.
				int rc = closeCurrentDocument();

				// If the user cancels out of it, quit the whole schibang.
				if (rc == JOptionPane.CANCEL_OPTION) {
					// If the newly-active file is read-only, say so in the status bar.
					owner.setStatusBarReadOnlyIndicatorEnabled(
						currentTextArea==null ? false
										: currentTextArea.isReadOnly());
					return false;
				}

			}

		} // End of for (int i=tabCount-1; i>=0; i--).

		// If we got this far, then all documents were closed.
		// We'll just have an empty default-named file out there.
		return true;

	}


	/**
	 * Attempts to close the current document.
	 *
	 * @return JOptionPane.YES_OPTION/NO_OPTION/CANCEL_OPTION, depending on
	 *         what the user does.
	 */
	public final int closeCurrentDocument() {

		RTextEditorPane old = currentTextArea;
		int rc = closeCurrentDocumentImpl();

		if (rc==JOptionPane.YES_OPTION) {

			old.clearParsers();

			// Remove the old language support.
			LanguageSupportFactory lsf = LanguageSupportFactory.get();
			LanguageSupport support = lsf.getSupport(old.getSyntaxEditingStyle());
			if (support!=null) {
				support.uninstall(old);
			}

			firePropertyChange(TEXT_AREA_REMOVED_PROPERTY, null, old);

		}

		return rc;

	}


	/**
	 * Attempts to close the current document.  Any implementation of this
	 * method <i>must be synchronized</i> so it doesn't interfere with the
	 * thread checking for files being modified outside of the editor.
	 *
	 * @return JOptionPane.YES_OPTION/NO_OPTION/CANCEL_OPTION, depending on
	 *         what the user does.
	 */
	protected abstract int closeCurrentDocumentImpl();


	/**
	 * Converts all instances of a number of spaces equal to a tab in all open
	 * documents into tabs.
	 *
	 * @see #convertOpenFilesTabsToSpaces
	 */
	public void convertOpenFilesSpacesToTabs() {
		int numDocuments = getNumDocuments();
		for (int i=0; i<numDocuments; i++)
			getRTextEditorPaneAt(i).convertSpacesToTabs();
	}


	/**
	 * Converts all tabs in all open documents into an equivalent number of
	 * spaces.
	 *
	 * @see #convertOpenFilesSpacesToTabs
	 */
	public void convertOpenFilesTabsToSpaces() {
		int numDocuments = getNumDocuments();
		for (int i=0; i<numDocuments; i++)
			getRTextEditorPaneAt(i).convertTabsToSpaces();
	}


	public void copyData(AbstractMainView fromPanel) {

		currentTextArea = fromPanel.currentTextArea;

		for (int i=0; i<NUM_ACTIONS; i++)
			actions[i] = fromPanel.getAction(i);

		findDialog		= fromPanel.findDialog;
		replaceDialog		= fromPanel.replaceDialog;
		searchStrings		= fromPanel.searchStrings;
		searchingForward	= fromPanel.searchingForward;
		searchMatchCase	= fromPanel.searchMatchCase;
		searchWholeWord	= fromPanel.searchWholeWord;
		searchRegExpression	= fromPanel.searchRegExpression;
		searchMarkAll		= fromPanel.searchMarkAll;
		lineNumbersEnabled	= fromPanel.lineNumbersEnabled;
		lineWrapEnabled	= fromPanel.lineWrapEnabled;

		findInFilesDialog	= fromPanel.findInFilesDialog;
		if (findInFilesDialog!=null) {
			findInFilesDialog.removeFindInFilesListener(fromPanel);
			findInFilesDialog.addFindInFilesListener(this);
		}
		replaceInFilesDialog = fromPanel.replaceInFilesDialog;
		if (replaceInFilesDialog!=null) {
			replaceInFilesDialog.removeFindInFilesListener(fromPanel);
			replaceInFilesDialog.addFindInFilesListener(this);
		}
		goToDialog		= fromPanel.goToDialog;

		textMode			= fromPanel.textMode;
		tabSize			= fromPanel.tabSize;
		emulateTabsWithWhitespace = fromPanel.emulateTabsWithWhitespace;

		printFont			= fromPanel.printFont;
		caretColor		= fromPanel.caretColor;
		selectionColor		= fromPanel.selectionColor;

		backgroundObject	= fromPanel.backgroundObject;
		imageAlpha		= fromPanel.imageAlpha;
		backgroundImageFileName	= fromPanel.backgroundImageFileName;

		owner			= fromPanel.owner;
		syntaxFilters		= fromPanel.syntaxFilters;

		setPreferredSize(fromPanel.getPreferredSize());

		int numDocuments = fromPanel.getNumDocuments();
		int fromSelectedIndex = fromPanel.getSelectedIndex();
		ArrayList scrollPanes = new ArrayList(numDocuments);
		for (int i=0; i<numDocuments; i++) {
			scrollPanes.add(fromPanel.getRTextScrollPaneAt(0));
			fromPanel.removeComponentAt(0);
		}
		for (int i=0; i<numDocuments; i++) {
			RTextScrollPane scrollPane = (RTextScrollPane)scrollPanes.get(i);
			RTextEditorPane editorPane = (RTextEditorPane)scrollPane.getTextArea();
			addTextAreaImpl(editorPane.getFileName(), scrollPane,
							editorPane.getFileFullPath());
			editorPane.removePropertyChangeListener(fromPanel);
			editorPane.removeHyperlinkListener(fromPanel);
			editorPane.addPropertyChangeListener(this);
			editorPane.addHyperlinkListener(this);
		}
		removeComponentAt(0);	// Remove the default-named file.
		renumberDisplayNames();	// In case the same document is opened multiple times.
		setSelectedIndex(fromSelectedIndex);

		spellingSupport = fromPanel.spellingSupport;

		// "Move over" all current text area listeners.
		// Remember "listeners" is guaranteed to be non-null.
		Object[] listeners = fromPanel.listenerList.getListenerList();
		Class ctalClass = CurrentTextAreaListener.class;
		for (int i=0; i<listeners.length; i+=2) {
			if (listeners[i]==ctalClass) {
				CurrentTextAreaListener l =
						(CurrentTextAreaListener)listeners[i+1];
				fromPanel.listenerList.remove(ctalClass, l);
				listenerList.add(ctalClass, l);
			}
		}

	}


	/**
	 * Creates the array of actions used by this RText.
	 *
	 * @param prefs The RText properties for this RText instance.
	 */
	private void createActions(RTextPreferences prefs) {

		ResourceBundle msg = ResourceBundle.getBundle(
								"org.fife.rtext.MainPanelActions");

		String commonIconPath = "org/fife/rtext/graphics/common_icons/";
		ClassLoader cl = this.getClass().getClassLoader();
		actions = new Action[NUM_ACTIONS];

		StandardAction a = new CloseAction(owner, msg, null);
		a.setAccelerator(prefs.mainViewActionAccelerators[CLOSE_ACTION]);
		actions[CLOSE_ACTION] = a;

		a = new CloseAllAction(owner, msg, null);
		a.setAccelerator(prefs.mainViewActionAccelerators[CLOSE_ALL_ACTION]);
		actions[CLOSE_ALL_ACTION] = a;

		a = new FindAction(owner, msg, null);
		a.setAccelerator(prefs.mainViewActionAccelerators[FIND_ACTION]);
		actions[FIND_ACTION] = a;

		a = new FindNextAction(owner, msg, null);
		a.setAccelerator(prefs.mainViewActionAccelerators[FIND_NEXT_ACTION]);
		actions[FIND_NEXT_ACTION] = a;

		a = new ReplaceAction(owner, msg, null);
		a.setAccelerator(prefs.mainViewActionAccelerators[REPLACE_ACTION]);
		actions[REPLACE_ACTION] = a;

		a = new ReplaceNextAction(owner, msg, null);
		a.setAccelerator(prefs.mainViewActionAccelerators[REPLACE_NEXT_ACTION]);
		actions[REPLACE_NEXT_ACTION] = a;

		a = new ReplaceAllAction(owner, msg, null);
		a.setAccelerator(prefs.mainViewActionAccelerators[REPLACE_ALL_ACTION]);
		actions[REPLACE_ALL_ACTION] = a;

		a = new FindInFilesAction(owner, msg, null);
		a.setAccelerator(prefs.mainViewActionAccelerators[FIND_IN_FILES_ACTION]);
		actions[FIND_IN_FILES_ACTION] = a;

		a = new ReplaceInFilesAction(owner, msg, null);
		a.setAccelerator(prefs.mainViewActionAccelerators[REPLACE_IN_FILES_ACTION]);
		actions[REPLACE_IN_FILES_ACTION] = a;

		a = new PrintAction(owner, msg, null);
		a.setAccelerator(prefs.mainViewActionAccelerators[PRINT_ACTION]);
		actions[PRINT_ACTION] = a;

		a = new PrintPreviewAction(owner, msg, null);
		a.setAccelerator(prefs.mainViewActionAccelerators[PRINT_PREVIEW_ACTION]);
		actions[PRINT_PREVIEW_ACTION] = a;

		a = new GoToAction(owner, msg, new ImageIcon(cl.getResource(commonIconPath+"goto16.gif")));
		a.setAccelerator(prefs.mainViewActionAccelerators[GOTO_ACTION]);
		actions[GOTO_ACTION] = a;

		a = new TextAreaOrientationAction(owner, msg, "LeftToRightAction", null,
				ComponentOrientation.LEFT_TO_RIGHT);
		a.setAccelerator(prefs.mainViewActionAccelerators[LTR_ACTION]);
		actions[LTR_ACTION] = a;

		a = new TextAreaOrientationAction(owner, msg, "RightToLeftAction", null,
				ComponentOrientation.RIGHT_TO_LEFT);
		a.setAccelerator(prefs.mainViewActionAccelerators[RTL_ACTION]);
		actions[RTL_ACTION] = a;

		a = new ViewSplitAction(owner, msg, null, "SplitHorizontallyAction",
								VIEW_SPLIT_HORIZ_ACTION);
		a.setAccelerator(prefs.mainViewActionAccelerators[VIEW_SPLIT_HORIZ_ACTION]);
		actions[VIEW_SPLIT_HORIZ_ACTION] = a;

		a = new ViewSplitAction(owner, msg, null, "SplitNoneAction",
								VIEW_SPLIT_NONE_ACTION);
		a.setAccelerator(prefs.mainViewActionAccelerators[VIEW_SPLIT_NONE_ACTION]);
		actions[VIEW_SPLIT_NONE_ACTION] = a;

		a = new ViewSplitAction(owner, msg, null, "SplitVerticallyAction",
								VIEW_SPLIT_VERT_ACTION);
		a.setAccelerator(prefs.mainViewActionAccelerators[VIEW_SPLIT_VERT_ACTION]);
		actions[VIEW_SPLIT_VERT_ACTION] = a;

		msg = null; // May help with GC.

	}


	protected ErrorStrip createErrorStrip(RTextEditorPane textArea) {
		ErrorStrip strip = new ErrorStrip(textArea);
		strip.setLevelThreshold(ParserNotice.WARNING);
		return strip;
	}


	/**
	 * Returns an editor pane to add to this main view.
	 *
	 * @param fileName The name of the file to add.
	 * @param encoding The encoding of the file.
	 * @param delegate If this value is non-null, it will be used to actually
	 *        create the editor pane.  Callers can pass in a delegate here
	 *        if they want to use enhanced text areas, for example.
	 * @return An editor pane.
	 * @throws IOException If an IO error occurs reading the file to load.
	 */
	private RTextEditorPane createRTextEditorPane(String fileName,
				String encoding, EditorPaneCreatorDelegate delegate)
				throws IOException {
		return createRTextEditorPane(FileLocation.create(fileName),
								encoding, delegate);
	}


	/**
	 * Returns an editor pane to add to this main view.
	 *
	 * @param loc The location of the file to add.
	 * @param encoding The encoding of the file.
	 * @param delegate If this value is non-null, it will be used to actually
	 *        create the editor pane.  Callers can pass in a delegate here
	 *        if they want to use enhanced text areas, for example.
	 * @return An editor pane.
	 * @throws IOException If an IO error occurs reading the file to load.
	 */
	private RTextEditorPane createRTextEditorPane(FileLocation loc,
				String encoding, EditorPaneCreatorDelegate delegate)
				throws IOException {

		String style = syntaxFilters.getSyntaxStyleForFile(loc.getFileName());

		// Actually create the pane.
		RTextEditorPane pane = null;
		if (delegate!=null) {
			pane = delegate.createRTextEditorPane(owner, lineWrapEnabled,
										textMode, loc, encoding);
		}
		else {
			pane = new RTextEditorPane(owner, lineWrapEnabled,
									textMode, loc, encoding);
		}

		// Set some properties.
		pane.setFont(textAreaFont);
		//pane.setUnderline(textAreaUnderline);
		pane.setForeground(textAreaForeground);
		pane.setBackgroundObject(backgroundObject);
		pane.setTabSize(tabSize);
		pane.setHighlightCurrentLine(highlightCurrentLine);
		pane.setCurrentLineHighlightColor(currentLineColor);
		pane.setMarginLineEnabled(marginLineEnabled);
		pane.setMarginLinePosition(marginLinePosition);
		pane.setMarginLineColor(marginLineColor);
		pane.setMarkAllHighlightColor(markAllHighlightColor);
		pane.setMarkOccurrences(markOccurrences);
		pane.setMarkOccurrencesColor(markOccurrencesColor);
		setSyntaxStyle(pane, style);
		pane.setBracketMatchingEnabled(bracketMatchingEnabled);
		pane.setMatchedBracketBGColor(matchedBracketBGColor);
		pane.setMatchedBracketBorderColor(matchedBracketBorderColor);
		if (defaultLineTerminator!=null &&
				pane.getDocument().getLength()==0) {
			// Empty (or new) file => use default line terminator.
			pane.setLineSeparator(defaultLineTerminator, false);
		}
		pane.setWhitespaceVisible(whitespaceVisible);
		pane.setCaretColor(caretColor);
		pane.setSelectionColor(selectionColor);
		pane.setSyntaxScheme(owner.getSyntaxScheme());
		pane.setHyperlinksEnabled(hyperlinksEnabled);
		pane.setHyperlinkForeground(hyperlinkColor);
		pane.setLinkScanningMask(hyperlinkModifierKey);
		pane.setRoundedSelectionEdges(roundedSelectionEdges);
		pane.setCaretStyle(RTextEditorPane.INSERT_MODE,
							carets[RTextEditorPane.INSERT_MODE]);
		pane.setCaretStyle(RTextEditorPane.OVERWRITE_MODE,
							carets[RTextEditorPane.OVERWRITE_MODE]);
		pane.getCaret().setBlinkRate(caretBlinkRate);
		//pane.setFadeCurrentLineHighlight(fadeCurrentLineHighlight);

		// If we're in the middle of recording a macro, make the cursor
		// appropriate on this guy.
		if (RTextEditorPane.isRecordingMacro()) {
			pane.setCursor(getMacroCursor());
		}

		// Other properties.
		pane.setTabsEmulated(emulateTabsWithWhitespace);
		pane.setTextAntiAliasHint(getTextAAHintName());
		pane.setFractionalFontMetricsEnabled(isFractionalFontMetricsEnabled());
		// orientation is done later to override scrollpane's
		// applyComponentOrientation(...).
		//pane.applyComponentOrientation(getTextAreaOrientation());

		// Listeners.
		pane.addPropertyChangeListener(owner);
		pane.addPropertyChangeListener((StatusBar)owner.getStatusBar());
		pane.addPropertyChangeListener(this);
		pane.addHyperlinkListener(this);

//CompletionProvider provider = new WordCompletionProvider(C_FUNCTIONS);
//AutoCompletion ac = new AutoCompletion(provider);
//ac.install(pane);

		// Add any parsers.
		if (spellingSupport.isSpellCheckingEnabled()) {
			pane.addParser(spellingSupport.getSpellingParser());
		}

		// Return him.
		return pane;

	}


	/**
	 * Creates and returns a scroll pane containing a text area.
	 *
	 * @param textArea The text area.
	 * @return The scroll pane.
	 */
	private RTextScrollPane createScrollPane(RTextEditorPane textArea) {
		RTextScrollPane scrollPane = new RTextScrollPane(textArea,
						lineNumbersEnabled, null);
		scrollPane.applyComponentOrientation(getComponentOrientation());
		Gutter gutter = scrollPane.getGutter();
		gutter.setBookmarkIcon(bookmarkIcon);
		gutter.setBookmarkingEnabled(bookmarksEnabled);
		gutter.setLineNumberFont(lineNumberFont);
		gutter.setLineNumberColor(lineNumberColor);
		gutter.setBorderColor(gutterBorderColor);
		// Always visible, makes life easier
		scrollPane.setIconRowHeaderEnabled(true);
		return scrollPane;
	}


	/**
	 * Ensures at least 1 file is open.
	 */
	private void ensureFilesAreOpened() {
		if (getNumDocuments()==0) {
			addNewEmptyUntitledFile();
		}
	}


	/**
	 * Called when the user selects a file in a listened-to find-in-files
	 * dialog.
	 *
	 * @param e The event received from the <code>FindInFilesDialog</code>.
	 */
	public void findInFilesFileSelected(FindInFilesEvent e) {
		String fileName = e.getFileName();
		// "null" encoding means check for Unicode before using default.
		// "true" means reuse an already-opened copy of the file if
		// one exists.
		if (!openFile(fileName, null, true)) {
			JOptionPane.showMessageDialog(findInFilesDialog,
					owner.getString("ErrorReloadFNF"),
					owner.getString("ErrorDialogTitle"),
					JOptionPane.ERROR_MESSAGE);
			return;
		}
		FindInFilesDialog fnfd = (FindInFilesDialog)e.getSource();
		String desc = owner.getString("FileOpened", fileName);
		fnfd.setStatusText(desc);
		int line = e.getLine();
		if (line!=-1) {
			try {

				// currentTextArea is updated here.  Highlight the searched-for
				// text.
				int start = currentTextArea.getLineStartOffset(line-1);
				int end = currentTextArea.getLineEndOffset(line-1) - 1;
				currentTextArea.setCaretPosition(end);
				currentTextArea.moveCaretPosition(start);
				currentTextArea.getCaret().setSelectionVisible(true);

			} catch (Exception exc) {
				owner.displayException(exc);
				moveToTopOfCurrentDocument();
			}
		}
		else
			moveToTopOfCurrentDocument();
	}


	/**
	 * Notifies all registered <code>CurrentTextAreaListener</code>s of a
	 * change in the current text area.
	 *
	 * @param type The type of event to fire.
	 * @param oldValue The old value.
	 * @param newValue The new value.
	 */
	protected void fireCurrentTextAreaEvent(int type, Object oldValue,
									Object newValue) {
		// Guaranteed to return a non-null array.
		Object[] listeners = listenerList.getListenerList();
		// Process the listeners last to first, notifying
		// those that are interested in this event.
		for (int i = listeners.length-2; i>=0; i-=2) {
			if (listeners[i]==CurrentTextAreaListener.class) {
				((CurrentTextAreaListener)listeners[i+1]).
					currentTextAreaPropertyChanged(
						new CurrentTextAreaEvent(this, type,
											oldValue, newValue));
			}
		}
	}


	/**
	 * Returns the requested action.
	 *
	 * @param action The action to fetch, e.g., <code>FIND_ACTION</code> or
	 *        <code>PRINT_ACTION</code>.
	 * @see #getActions
	 */
	public /*RecordableTextAction*/Action getAction(int action) {
		return actions[action];
	}


	/**
	 * Returns all actions owned by this main view.
	 *
	 * @return All actions owned by this main view.
	 * @see #getAction
	 */
	public Action[] getActions() {
		return actions;
	}


	/**
	 * Returns the alpha value used to make the background image translucent.
	 * This value does NOT change the background if no image is used (i.e., if
	 * the background is just a color).
	 *
	 * @return The alpha value used to make a background image translucent.
	 *         This value will be in the range 0.0f to 1.0f.
	 * @see #setBackgroundImageAlpha
	 */
	public float getBackgroundImageAlpha() {
		return imageAlpha;
	}


	/**
	 * Returns the full path to the file containing the current background
	 * image.
	 *
	 * @return The path, or <code>null</code> if the current background is a
	 *         color.
	 * @see #setBackgroundImageFileName
	 */
	public String getBackgroundImageFileName() {
		return backgroundImageFileName;
	}


	/**
	 * Returns the <code>Object</code> representing the background for all
	 * documents in this tabbed pane; either a <code>java.awt.Color</code> or a
	 * <code>java.lang.String</code>.
	 */
	public Object getBackgroundObject() {
		return backgroundObject;
	}


	/**
	 * Returns whether bookmarks are enabled.
	 *
	 * @return Whether bookmarks are enabled.
	 * @see #setBookmarksEnabled(boolean)
	 */
	public boolean getBookmarksEnabled() {
		return bookmarksEnabled;
	}


	/**
	 * Returns the blink rate for all text areas.
	 *
	 * @return The blink rate.
	 * @see #setCaretBlinkRate
	 */
	public int getCaretBlinkRate() {
		return caretBlinkRate;
	}


	/**
	 * Returns the color being used for the carets of all text areas in this
	 * main view.
	 *
	 * @return The <code>java.awt.Color</code> being used for all carets.
	 */
	public Color getCaretColor() {
		return caretColor;
	}


	/**
	 * Returns the caret style for either the insert or overwrite caret.
	 *
	 * @param mode Either <code>RTextArea.INSERT_MODE</code> or
	 *        <code>RTextArea.OVERWRITE_MODE</code>.
	 * @return The style of that caret, such as
	 *        <code>ConfigurableCaret.VERTICAL_LINE_STYLE</code>.
	 * @see #setCaretStyle
	 * @see org.fife.ui.rtextarea.ConfigurableCaret
	 */
	public int getCaretStyle(int mode) {
		return carets[mode];
	}


	/**
	 * Returns the color being used to highlight the current line.  Note that
	 * if highlighting the current line is turned off, you will not be seeing
	 * this highlight.
	 *
	 * @return The color being used to highlight the current line.
	 * @see #isCurrentLineHighlightEnabled
	 * @see #setCurrentLineHighlightEnabled
	 * @see #setCurrentLineHighlightColor
	 */
	public Color getCurrentLineHighlightColor() {
		return currentLineColor;
	}


	/**
	 * Returns the currently active text area.
	 *
	 * @return The currently active text area.
	 */
	public RTextEditorPane getCurrentTextArea() {
		return currentTextArea;
	}


	/**
	 * Returns the default encoding of new text files.
	 *
	 * @return The default encoding.
	 * @see #setDefaultEncoding(String)
	 */
	public String getDefaultEncoding() {
		return defaultEncoding;
	}


	/**
	 * Returns the name and path of the "default file;" that is, the file
	 * that is created when the user selects "New".
	 *
	 * @return The new, empty file's name.
	 */
	private final String getDefaultFileName() {
		return owner.getWorkingDirectory() +
			System.getProperty("file.separator") + owner.getNewFileName();
	}


	/**
	 * Returns whether a file's size should be checked before it is opened.
	 *
	 * @return Whether a file's size is checked before it is opened.
	 * @see #setDoFileSizeCheck(boolean)
	 */
	public boolean getDoFileSizeCheck() {
		return doFileSizeCheck;
	}


	/**
	 * Returns the name being displayed for the document.  For example, in a
	 * tabbed pane subclass, this could be the text on the tab for this
	 * document.
	 *
	 * @param index The index at which to find the name.  If the index is
	 *        invalid, <code>null</code> is returned.
	 * @return The name being displayed for this document.
	 */
	public abstract String getDocumentDisplayNameAt(int index);


	/**
	 * Returns the location of the document selection area of this component.
	 *
	 * @return The location of the document selection area.
	 */
	public abstract int getDocumentSelectionPlacement();


	/**
	 * Returns the index of the specified document.
	 *
	 * @return The index of the specified file, or <code>-1</code> if the file
	 *         is not being edited.
	 */
	public int getFileIndex(String fileFullPath) {
		int numDocuments = getNumDocuments();
		for (int i=0; i<numDocuments; i++) {
			if (getRTextEditorPaneAt(i).getFileFullPath().equals(fileFullPath))
				return i;
		}
		return -1;
	}


	/**
	 * If the user has set a maximum file size to open, they are prompted
	 * whether they are "sure" they want to open the file if it is over
	 * their set size.
	 *
	 * @param fileName The file to check.
	 * @return If they do not want to check files of a certain size, this
	 *         method will return <code>false</code>.  Otherwise, it will
	 *         return <code>true</code> if and only if this file is larger
	 *         than their threshold and they chose not to open it.
	 */
	private boolean getFileIsTooLarge(String fileName) {
		if (getDoFileSizeCheck()) {
			File file = new File(fileName);
			float fileSizeMB = file.length() / 1000000.0f;
			float maxFileSizeMB = getMaxFileSize();
			if (fileSizeMB>maxFileSizeMB) {
				String desc = owner.getString("OpeningLargeFile",
										file.getAbsolutePath());
				int rc = JOptionPane.showConfirmDialog(this, desc,
					owner.getString("ConfDialogTitle"),
					JOptionPane.YES_NO_OPTION);
				if (rc!=JOptionPane.YES_OPTION) {
					// Keep at least 1 document open.
					ensureFilesAreOpened();
					return true;
				}
			}
		}
		return false;
	}


	/**
	 * Returns whether files with no extension have their content type guessed
	 * at via whether they have a "<code>#!</code>" in their first line.
	 *
	 * @return Whether to guess the content type of files with no
	 *         extension.
	 * @see #setGuessFileContentType(boolean)
	 */
	public boolean getGuessFileContentType() {
		return guessFileContentType;
	}


	/**
	 * Returns the color to use for the gutter border color.
	 *
	 * @return The gutter border color.
	 * @see #setGutterBorderColor(Color)
	 */
	public Color getGutterBorderColor() {
		return gutterBorderColor;
	}


	/**
	 * Returns the color editors use for hyperlinks.
	 *
	 * @return The color used.
	 * @see #setHyperlinkColor(Color)
	 */
	public Color getHyperlinkColor() {
		return hyperlinkColor;
	}


	/**
	 * Returns the modifier key editors use to scan for hyperlinks.
	 *
	 * @return The modifier key(s).
	 * @see #setHyperlinkModifierKey(int)
	 * @see java.awt.event.InputEvent
	 */
	public int getHyperlinkModifierKey() {
		return hyperlinkModifierKey;
	}


	/**
	 * Returns whether hyperlinks are enabled in text editors.
	 *
	 * @return Whether hyperlinks are enabled.
	 * @see #setHyperlinksEnabled(boolean)
	 */
	public boolean getHyperlinksEnabled() {
		return hyperlinksEnabled;
	}


	/**
	 * Returns the system icon associated with the file being edited in the
	 * given scroll pane (actually, in the text area inside of it).  This
	 * method is called by subclasses that want to display a system icon for
	 * open files.
	 *
	 * @param scrollPane The scroll pane.
	 * @return The icon.
	 */
	protected Icon getIconFor(RTextScrollPane scrollPane) {
		RTextEditorPane textArea = (RTextEditorPane)scrollPane.
								getTextArea();
		return FileTypeIconManager.getInstance().getIconFor(textArea);
	}


	/**
	 * Sets the color used for line numbers.
	 *
	 * @return The line number color.
	 * @see #setLineNumberColor(Color)
	 */
	public Color getLineNumberColor() {
		return lineNumberColor;
	}


	/**
	 * Returns the font used for line numbers.
	 *
	 * @return The line number font.
	 * @see #setLineNumberFont(Font)
	 */
	public Font getLineNumberFont() {
		return lineNumberFont;
	}


	/**
	 * Returns whether or not line numbers are visible in the open documents.
	 */
	public boolean getLineNumbersEnabled() {
		return lineNumbersEnabled;
	}


	/**
	 * Returns the line terminator to use for new text files.
	 *
	 * @return The line terminator.
	 * @see #setLineTerminator(String)
	 */
	public String getLineTerminator() {
		return defaultLineTerminator;
	}


	/**
	 * Returns whether or not line (word) wrap is enabled for the open
	 * documents.
	 */
	public boolean getLineWrap() {
		return lineWrapEnabled;
	}


	/**
	 * Returns the cursor to use when a macro is being recorded.
	 *
	 * @return The cursor for text areas when a macro is being recorded.
	 */
	private static synchronized final Cursor getMacroCursor() {

		if (macroCursor==null) {

			// OS's will force the cursor to be a certain size (e.g., Windows
			// will make it 32x32 usually), even if you try to set the cursor
			// to some other size.  So, we create a cursor that is the "best"
			// cursor size to prevent our cursor image from being stretched.

			try {

				java.awt.Toolkit toolkit = java.awt.Toolkit.getDefaultToolkit();
				java.awt.Dimension dim = toolkit.getBestCursorSize(16,16);
				BufferedImage transparentImage = new BufferedImage(
										dim.width, dim.height,
										BufferedImage.TYPE_INT_ARGB);

				Graphics2D g2d = transparentImage.createGraphics();
				BufferedImage image = null;

				ClassLoader cl = AbstractMainView.class.getClassLoader();
				image = ImageIO.read(cl.getResource(
							"org/fife/rtext/graphics/macrocursor.gif"));
				g2d.drawImage(image,0,0,null);
				g2d.dispose();

				Point hotspot = new Point(0,0);
				macroCursor = toolkit.createCustomCursor(transparentImage,
											hotspot, "macroCursor");

			// If something fails (such as the cursor image not being found),
			// just use the crosshair cursor.
			} catch (RuntimeException re) {
				throw re; // Keep FindBugs happy - don't mask RuntimeExceptions
			} catch (Exception e) {
				macroCursor = Cursor.getPredefinedCursor(
										Cursor.CROSSHAIR_CURSOR);
			}

		} // End of if (macroCursor==null).

		return macroCursor;

	}


	/**
	 * Returns the margin line's color.
	 *
	 * @return The color of the margin (even if it is not enabled).
	 * @see #setMarginLineColor
	 */
	public Color getMarginLineColor() {
		return marginLineColor;
	}


	/**
	 * Returns the position of the margin line (even if it is not enabled).
	 *
	 * @return The position of the margin line.
	 * @see #setMarginLinePosition
	 */
	public int getMarginLinePosition() {
		return marginLinePosition;
	}


	/**
	 * Returns the color selected by the user for "mark all."
	 *
	 * @return The color.
	 * @see #setMarkAllHighlightColor
	 */
	public Color getMarkAllHighlightColor() {
		return markAllHighlightColor;
	}


	/**
	 * Returns whether "mark occurrences" is enabled.
	 *
	 * @return Whether "mark occurrences" is enabled.
	 * @see #setMarkOccurrences(boolean)
	 */
	public boolean getMarkOccurrences() {
		return markOccurrences;
	}


	/**
	 * Returns the color to use to "mark occurrences."
	 *
	 * @return The color.
	 * @see #setMarkOccurrencesColor(Color)
	 * @see #getMarkOccurrences()
	 */
	public Color getMarkOccurrencesColor() {
		return markOccurrencesColor;
	}


	/**
	 * Returns the background color used in bracket matching.
	 *
	 * @return The background color used when highlighting a bracket.
	 * @see #setMatchedBracketBGColor
	 */
	public Color getMatchedBracketBGColor() {
		return matchedBracketBGColor;
	}


	/**
	 * Returns the border color used in bracket matching.
	 *
	 * @return The border color used when highlighting a bracket.
	 * @see #setMatchedBracketBorderColor
	 */
	public Color getMatchedBracketBorderColor() {
		return matchedBracketBorderColor;
	}


	/**
	 * If file-size checking is enabled, this is the maximum size a
	 * file can be before the user is prompted before opening it.
	 *
	 * @return The maximum file size.
	 * @see #setMaxFileSize(float)
	 * @see #getDoFileSizeCheck()
	 */
	public float getMaxFileSize() {
		return maxFileSize;
	}


	/**
	 * Gets the color used to highlight modified documents' display names.
	 *
	 * @return color The color used.
	 * @see #setModifiedDocumentDisplayNamesColor
	 * @see #highlightModifiedDocumentDisplayNames
	 * @see #setHighlightModifiedDocumentDisplayNames
	 */
	public Color getModifiedDocumentDisplayNamesColor() {
		return modifiedDocumentDisplayNameColor;
	}


	/**
	 * Returns the number of documents open in this container.
	 *
	 * @return The number of open documents.
	 */
	public abstract int getNumDocuments();


	/**
	 * Returns all of the files opened in this main view.
	 *
	 * @return An array of files representing the files being edited in this
	 *         main view.  If no documents are open, <code>null</code> is
	 *         returned.
	 */
	public File[] getOpenFiles() {

		int num = getNumDocuments();
		if (num==0)
			return null;
		File[] files = new File[num];

		for (int i=0; i<num; i++) {
			files[i] = new File(getRTextEditorPaneAt(i).getFileFullPath());
		}

		return files;

	}


	/**
	 * Returns the <code>java.awt.Font</code> currently used to print documents.
	 *
	 * @return The font used to print documents.  If <code>null</code> is
	 *         returned, that means the current RText font is being used to
	 *         print documents.
	 * @see #setPrintFont
	 */
	public Font getPrintFont() {
		return printFont;
	}


	/**
	 * Returns whether selection edges are rounded in text areas.
	 *
	 * @return Whether selection edges are rounded.
	 * @see #setRoundedSelectionEdges
	 */
	public boolean getRoundedSelectionEdges() {
		return roundedSelectionEdges;
	}


	/**
	 * Returns the <code>org.fife.rtext.RTextEditorPane</code> on the
	 * specified tab.  This is a convenience method for
	 * <code>(RTextEditorPane)getRTextScrollPaneAt(i).textArea</code>.
	 *
	 * @param index The tab for which you want to get the
	 *        {@link org.fife.rtext.RTextEditorPane}.
	 * @return The corresponding {@link org.fife.rtext.RTextEditorPane}.
	 */
	public RTextEditorPane getRTextEditorPaneAt(int index) {
		RTextScrollPane sp = getRTextScrollPaneAt(index);
		return sp!=null ? (RTextEditorPane)sp.getTextArea() : null;
	}


	/**
	 * Returns the <code>org.fife.rtext.RTextScrollPane</code> at the given
	 * index.
	 *
	 * @param index The tab for which you want to get the
	 *        <code>org.fife.rtext.RTextScrollPane</code>.
	 * @return The scroll pane.
	 */
	public abstract RTextScrollPane getRTextScrollPaneAt(int index);


	/**
	 * Returns the currently active component.
	 *
	 * @return The component.
	 */
	public abstract Component getSelectedComponent();


	/**
	 * Returns the currently selected document's index.
	 *
	 * @return The index of the currently selected document.
	 */
	public abstract int getSelectedIndex();


	/**
	 * Returns the color being used for selections in all text areas in this
	 * main view.
	 *
	 * @return The <code>java.awt.Color</code> being used for all selections in
	 *         all text areas.
	 */
	public Color getSelectionColor() {
		return selectionColor;
	}


	/**
	 * Returns the spell checking support for RText.
	 *
	 * @return The spell checking support.
	 */
	public SpellingSupport getSpellingSupport() {
		return spellingSupport;
	}


	/**
	 * Returns the syntax filters being used to open documents (i.e., to decide
	 * what syntax highlighting color scheme, if any, to use when opening
	 * documents).
	 *
	 * @return The filters being used.
	 */
	public SyntaxFilters getSyntaxFilters() {
		return syntaxFilters;
	}


	/**
	 * Returns the size of a tab, in spaces.
	 *
	 * @return The tab size (in spaces) currently being used by all documents
	 *         in this tabbed pane.
	 */
	public int getTabSize() {
		return tabSize;
	}


	/**
	 * Returns the name of the scheme being used to antialias the text
	 * editors' text, if any.
	 *
	 * @return The name of a field of <code>java.awt.RenderingHints</code>
	 *         that is being used for text AA.  A <code>null</code> value
	 *         means no text AA is being done.
	 * @see #isFractionalFontMetricsEnabled
	 * @see #setTextAAHintName(String)
	 */
	public String getTextAAHintName() {
		return aaHintFieldName;
	}


	/**
	 * Returns the default font to use in text areas.
	 *
	 * @return The default font.
	 * @see #getTextAreaUnderline()
	 * @see #setTextAreaFont(Font, boolean)
	 */
	public Font getTextAreaFont() {
		return textAreaFont;
	}


	/**
	 * Returns the default foreground color for text areas.
	 *
	 * @return The default foreground color.
	 * @see #setTextAreaForeground(Color)
	 */
	public Color getTextAreaForeground() {
		return textAreaForeground;
	}


	/**
	 * Returns the orientation of the text editors.
	 *
	 * @return The orientation.
	 * @see #setTextAreaOrientation(ComponentOrientation)
	 */
	public ComponentOrientation getTextAreaOrientation() {
		return textAreaOrientation;
	}


	/**
	 * Returns whether text areas' default fonts should be underlined.
	 *
	 * @return Whether to underline the default font.
	 * @see #getTextAreaFont()
	 * @see #setTextAreaFont(Font, boolean)
	 */
	public boolean getTextAreaUnderline() {
		return textAreaUnderline;
	}


	/**
	 * Returns the text mode we're in.
	 *
	 * @return <code>RTextEditorPane.INSERT_MODE</code>
	 *         or <code>RTextEditorPane.OVERWRITE_MODE</code>.
	 * @see #setTextMode
	 */
	public int getTextMode() {
		return textMode;
	}


	/**
	 * Returns a translucent version of a given <code>java.awt.Image</code>.
	 *
	 * @param image The <code>java.awt.Image</code> on which to apply the
	 *        alpha filter.
	 * @param alpha The alpha value to use when defining how translucent you
	 *        want the image to be. This should be in the range 0.0f to 1.0f.
	 */
	private BufferedImage getTranslucentImage(Image image, float alpha) {

		BufferedImage buffer = null;

		int width = image.getWidth(null);
		int height = image.getHeight(null);
		MediaTracker tracker = new MediaTracker(this);
		buffer = new BufferedImage(width,height, BufferedImage.TYPE_INT_ARGB_PRE);
		tracker.addImage(buffer, 0);
		Graphics2D g2d = buffer.createGraphics();
		g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
		g2d.drawImage(image, 0,0, null);
		try {
			tracker.waitForID(0);
		} catch (InterruptedException e) {
			owner.displayException(e);
		}
		tracker.removeImage(buffer, 0);
		g2d.dispose();

		return buffer;

	}


	/**
	 * Returns whether BOM's are written for UTF-8 files.
	 *
	 * @return Whether BOM's are written for UTF-8 files.
	 * @see #setWriteBOMInUtf8Files(boolean)
	 */
	public boolean getWriteBOMInUtf8Files() {
		return UnicodeWriter.getWriteUtf8BOM();
	}


	/**
	 * Sets the pane's highlighting style based on its content (e.g. whether
	 * it contains "<code>#!</code>" at the top).
	 *
	 * @param pane The pane to examine.
	 */
	private void guessContentType(RTextEditorPane pane) {

		String style = SyntaxConstants.SYNTAX_STYLE_NONE;

		String firstLine = null;
		try {
			int endOffs = pane.getLineEndOffset(0);
			if (pane.getLineCount()>1) {
				endOffs--;
			}
			firstLine = pane.getText(0, endOffs);
		} catch (BadLocationException ble) { // Never happens
			ble.printStackTrace();
			return;
		}

		if (firstLine.startsWith("#!")) {

			// Determine the program name.  Take special care for
			// the case of "#!/usr/bin/env progname".
			int space = firstLine.indexOf(' ', 2);
			if (space>-1) {
				if (firstLine.startsWith("#!/usr/bin/env")) {
					int space2 = firstLine.indexOf(' ', space+1);
					if (space2==-1) { // Never happens in "correct" #!'s
						space2 = firstLine.length();
					}
					firstLine = firstLine.substring(space+1, space2);	
				}
				else {
					firstLine = firstLine.substring(2, space);
				}
			}

			if (firstLine.endsWith("sh")) { // ksh, bash, sh, ...
				style = SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL;
			}
			else if (firstLine.endsWith("perl")) {
				style = SyntaxConstants.SYNTAX_STYLE_PERL;
			}
			else if (firstLine.endsWith("php")) {
				style = SyntaxConstants.SYNTAX_STYLE_PHP;
			}
			else if (firstLine.endsWith("python")) {
				style = SyntaxConstants.SYNTAX_STYLE_PYTHON;
			}
			else if (firstLine.endsWith("lua")) {
				style = SyntaxConstants.SYNTAX_STYLE_LUA;
			}
			else if (firstLine.endsWith("ruby")) {
				style = SyntaxConstants.SYNTAX_STYLE_RUBY;
			}

		}

		pane.setSyntaxEditingStyle(style);

	}


	/**
	 * Handles <code>IOException</code>s thrown when adding an old text file
	 * to RText.  This method displays a dialog showing the error message,
	 * prettied-up if possible.
	 *
	 * @param loc The location of the file being opened or saved.
	 * @param ioe The exception thrown.
	 * @param load Whether this was a load operation.  If this value is
	 *        <code>false</code>, then this was a save operation.
	 */
	private void handleAddTextFileIOException(FileLocation loc,
								IOException ioe, boolean load) {

		String desc = null;
		String title = owner.getString("ErrorDialogTitle");

		if ("sun.net.ftp.FtpLoginException".equals(ioe.getClass().getName())){
			desc = owner.getString("ErrorCredentials");
			JOptionPane.showMessageDialog(this, desc, title,
								JOptionPane.ERROR_MESSAGE);
		}
		else if (ioe instanceof java.net.ConnectException) {
			desc = owner.getString("ErrorConnectionRefused");
			JOptionPane.showMessageDialog(this, desc, title,
								JOptionPane.ERROR_MESSAGE);
		}
		else if (ioe instanceof java.net.UnknownHostException) {
			String host = ioe.getMessage(); // Usually the hostname.
			if (host==null) {
				host = "";
			}
			desc = owner.getString("ErrorUnknownHost", host);
			JOptionPane.showMessageDialog(this, desc, title,
								JOptionPane.ERROR_MESSAGE);
		}
		else if (ioe instanceof java.io.FileNotFoundException) {
			// For save operations, FileNotFoundException usually means
			// "Access denied" or some other hard-to-decipher-from-a-stack-
			// trace message, so for saves, just print the exception's
			// message instead of a pretty one.
			if (load) {
				desc = owner.getString("ErrorFileNotFound",
								loc.getFileName());
				JOptionPane.showMessageDialog(this, desc, title,
								JOptionPane.ERROR_MESSAGE);
			}
			else { // Prevent "An unexpected error occurred"
				desc = ioe.getMessage();
				if (desc==null) {
					desc = ioe.toString();
				}
				owner.displayException(ioe, desc);
			}
		}
		else {
			desc = ioe.getMessage();
			if (desc==null) {
				desc = ioe.toString();
			}
			owner.displayException(ioe, desc);
		}

	}


	private void handleFileModifiedEvent(String text) {

		StringTokenizer tokenizer = new StringTokenizer(
				text.substring(text.indexOf(' ')));

		// Loop while there are still documents to prompt for.
		while (tokenizer.hasMoreTokens()) {

			// Should be the number of the next modified document.
			String token = tokenizer.nextToken();

			int docNumber = 0;
			try {
				docNumber = Integer.parseInt(token);
			} catch (NumberFormatException nfe) { // Should never happen
				continue;
			}
			setSelectedIndex(docNumber);

			// We must get it as a regular expression because
			// replaceFirst expects one.
			int rc = JOptionPane.NO_OPTION;
			String temp = owner.getString("DocModifiedMessage",
								currentTextArea.getFileName());
			rc = JOptionPane.showConfirmDialog(owner, temp,
							owner.getString("ConfDialogTitle"),
							JOptionPane.YES_NO_OPTION,
							JOptionPane.QUESTION_MESSAGE);

			// If they want to, reload the file.
			if (rc==JOptionPane.YES_OPTION) {

				try {
					File f = new File(currentTextArea.getFileFullPath());
					if (f.isFile()) { // Should always be true.
						currentTextArea.reload();
						moveToTopOfCurrentDocument();
					}
					else {
						JOptionPane.showMessageDialog(owner,
							owner.getString("ErrorReloadFNF"),
							owner.getString("ErrorDialogTitle"),
							JOptionPane.ERROR_MESSAGE);
					}
				} catch (Exception ioe) {
					JOptionPane.showMessageDialog(owner,
						owner.getString("ErrorReadingFile") + ioe,
						owner.getString("ErrorDialogTitle"),
						JOptionPane.ERROR_MESSAGE);
				}

			} // End of if (rc==JOptionPane.YES_OPTION)

			// Whether or not we reload, we need to update the "last
			// modified" time for this document, so we don't keep
			// bugging them about the same outside modification.
			currentTextArea.syncLastSaveOrLoadTimeToActualFile();

		} // End of while (token != null).

		// It's okay to start checking for modifications again.
		checkForModification = true;

	}


	/**
	 * Returns whether this panel is highlighting modified documents' display
	 * names with a different color.
	 *
	 * @see #setHighlightModifiedDocumentDisplayNames
	 * @see #getModifiedDocumentDisplayNamesColor
	 * @see #setModifiedDocumentDisplayNamesColor
	 */
	public boolean highlightModifiedDocumentDisplayNames() {
		return highlightModifiedDocDisplayNames;
	}


	/**
	 * Called when a hyperlink is entered, exited, or clicked in the
	 * current text area.
	 *
	 * @param e The hyperlink event.
	 */
	public void hyperlinkUpdate(HyperlinkEvent e) {
		if (e.getEventType()==HyperlinkEvent.EventType.ACTIVATED) {
			URL url = e.getURL();
			if (url!=null) {
				if (!UIUtil.browse(url.toString())) {
					UIManager.getLookAndFeel().provideErrorFeedback(this);
				}
			}
			else {
				UIManager.getLookAndFeel().provideErrorFeedback(this);
			}
		}
	}


	/**
	 * Initializes this component.
	 *
	 * @param owner The <code>RText</code> that this tabbed pane sits in.
	 * @param filesToOpen Array of strings representing files to open.  If
	 *        this parameter is null, a single file with a default name is
	 *        opened.
	 * @param prefs A properties object used to initialize some fields on
	 *        this main view.
	 */
	protected void initialize(RText owner, String[] filesToOpen,
										RTextPreferences prefs) {

		// Remember the owner of this tabbed pane.
		this.owner = owner;

		// Initialize some stuff from prefs.
		printFont = prefs.printFont;
		tabSize = prefs.tabSize;
		textMode = prefs.textMode;
		emulateTabsWithWhitespace = prefs.emulateTabsWithSpaces;
		setDocumentSelectionPlacement(prefs.tabPlacement);
		lineNumbersEnabled = prefs.lineNumbersVisible;
		setBackgroundImageAlpha(prefs.imageAlpha);
		Object prefsBackgroundObject = prefs.backgroundObject;
		if (prefsBackgroundObject instanceof String) {
			Image image = RTextUtilities.getImageFromFile(
									(String)prefsBackgroundObject);
			if (image!=null) {
				setBackgroundObject(image);
				setBackgroundImageFileName((String)prefsBackgroundObject);
			}
			else {	// This is when the file passed in no longer exists.
				setBackgroundObject(Color.WHITE);
				setBackgroundImageFileName(null);
			}
		}
		else {	// It must be a color here.
			setBackgroundObject(prefsBackgroundObject);
			setBackgroundImageFileName(null);
		}
		setCaretColor(prefs.caretColor);
		setSelectionColor(prefs.selectionColor);
		setLineWrap(prefs.wordWrap);
		setCurrentLineHighlightEnabled(prefs.currentLineHighlightEnabled);
		setCurrentLineHighlightColor(prefs.currentLineHighlightColor);
		setBracketMatchingEnabled(prefs.bracketMatchingEnabled);
		setMatchedBracketBGColor(prefs.matchedBracketBGColor);
		setMatchedBracketBorderColor(prefs.matchedBracketBorderColor);
		setMarginLineEnabled(prefs.marginLineEnabled);
		setMarginLinePosition(prefs.marginLinePosition);
		setMarginLineColor(prefs.marginLineColor);
		setHyperlinksEnabled(prefs.hyperlinksEnabled);
		setHyperlinkColor(prefs.hyperlinkColor);
		setHyperlinkModifierKey(prefs.hyperlinkModifierKey);
		setWriteBOMInUtf8Files(prefs.bomInUtf8);

		syntaxFilters = new SyntaxFilters(prefs.syntaxFiltersString);

		searchStrings = new Vector(0);
		searchingForward = true;	// Default to searching forward.
		searchMatchCase = false;	// Default is don't match case.
		searchWholeWord = false;	// Default is not whole word.
		searchMarkAll   = false; // Default is not to mark all.

		// Initialize this main view's actions.
		createActions(prefs);

		setHighlightModifiedDocumentDisplayNames(prefs.highlightModifiedDocNames);
		setModifiedDocumentDisplayNamesColor(prefs.modifiedDocumentNamesColor);
		setWhitespaceVisible(prefs.visibleWhitespace);
		setTextAAHintName(prefs.textAAHintFieldName);
		setFractionalFontMetricsEnabled(prefs.fractionalMetricsEnabled);
		setMarkAllHighlightColor(prefs.markAllHighlightColor);
		setMarkOccurrences(prefs.markOccurrences);
		setMarkOccurrencesColor(prefs.markOccurrencesColor);
		setRoundedSelectionEdges(prefs.roundedSelectionEdges);
		carets = new int[2];
		setCaretStyle(RTextArea.INSERT_MODE, prefs.carets[0]);
		setCaretStyle(RTextArea.OVERWRITE_MODE, prefs.carets[1]);
		setCaretBlinkRate(prefs.caretBlinkRate);
		setLineTerminator(prefs.defaultLineTerminator);
		setDefaultEncoding(prefs.defaultEncoding);
		setGuessFileContentType(prefs.guessFileContentType);
		setDoFileSizeCheck(prefs.doFileSizeCheck);
		setMaxFileSize(prefs.maxFileSize);
		setTextAreaFont(prefs.textAreaFont, prefs.textAreaUnderline);
		setTextAreaForeground(prefs.textAreaForeground);
		setTextAreaOrientation(prefs.textAreaOrientation);

		setBookmarksEnabled(prefs.bookmarksEnabled);
		setLineNumberFont(prefs.lineNumberFont);
		setLineNumberColor(prefs.lineNumberColor);
		setGutterBorderColor(prefs.gutterBorderColor);
		spellingSupport = new SpellingSupport(owner);
		spellingSupport.configure(prefs); // Do this BEFORE opening any files!

		// Start us out with whatever files they passed in.
		if (filesToOpen==null) {
			addNewEmptyUntitledFile();
		}
		else {
			for (int i=0; i<filesToOpen.length; i++) {
				// The "null" encoding means they'll be checked for Unicode.
				openFile(filesToOpen[i], null);
			}
		}
		setSelectedIndex(0);

		// Update the title of the RText window.
		owner.setMessages(currentTextArea.getFileFullPath(), null);

	}


	/**
	 * Returns whether or not bracket matching is enabled.
	 *
	 * @return <code>true</code> iff bracket matching is enabled.
	 * @see #setBracketMatchingEnabled
	 */
	public boolean isBracketMatchingEnabled() {
		return bracketMatchingEnabled;
	}


	/**
	 * Returns whether or not the current line is highlighted.
	 *
	 * @return Whether or the current line is highlighted.
	 * @see #setCurrentLineHighlightEnabled
	 * @see #getCurrentLineHighlightColor
	 * @see #setCurrentLineHighlightColor
	 */
	public boolean isCurrentLineHighlightEnabled() {
		return highlightCurrentLine;
	}


	/**
	 * Returns whether fractional fontmetrics is enabled.
	 *
	 * @return Whether fractional fontmetrics is enabled.
	 * @see #getTextAAHintName()
	 * @see #setFractionalFontMetricsEnabled
	 */
	public boolean isFractionalFontMetricsEnabled() {
		return fractionalMetricsEnabled;
	}


	/**
	 * Returns whether or not the margin line is enabled
	 *
	 * @return Whether or not the margin line is enabled.
	 * @see #setMarginLineEnabled
	 */
	public boolean isMarginLineEnabled() {
		return marginLineEnabled;
	}


	/**
	 * Returns whether whitespace is visible in the text areas in this panel.
	 *
	 * @return Whether whitespace is visible.
	 * @see #setWhitespaceVisible
	 */
	public boolean isWhitespaceVisible() {
		return whitespaceVisible;
	}


	/**
	 * Loads a macro.  This macro will be loaded into all currently-open
	 * <code>RTextEditorPane</code>s, as well as all editor panes opened
	 * afterward, until a new macro is loaded.
	 *
	 * @param file The file containing the macro to load.  If this file does
	 *        not exist or is otherwise invalid, no macro is loaded.
	 */
	public void loadMacro(File file) {
		try {
			RTextEditorPane.loadMacro(new Macro(file));
		} catch (Exception e) {
			owner.displayException(e);
		}
	}


	/**
	 * Scrolls to the top of the current document, and places the cursor there.
	 */
	public void moveToTopOfCurrentDocument() {
		currentTextArea.setCaretPosition(0);
	}


	/**
	 * Adds a file to this tabbed pane.  This method is synchronized so it
	 * doesn't interfere with the thread checking for files being modified
	 * outside of the editor.
	 *
	 * @param loc The location of the file to add.
	 * @param charSet The encoding to use when reading/writing this file.
	 *        If this value is <code>null</code>, the file is checked for
	 *        Unicode; if it is Unicode, it is opened properly.  If it is not
	 *        Unicode, a system default encoding is used.
	 * @param delegate If non-null, this object will be used to create the
	 *        new text area.
	 * @param reuse If the file is already open, whether to simply switch
	 *        focus to that old copy (vs. opening a new copy).
	 * @return <code>true</code> if the file was opened (or switched to),
	 *         <code>false</code> otherwise (if the file does not exist and
	 *         the user chose NOT to create it, for example).
	 * @throws InvalidCharSetException If the specified character set is
	 *         invalid.
	 */
	public boolean openFile(FileLocation loc, String charSet,
					EditorPaneCreatorDelegate delegate, boolean reuse) {

		// If the only document open is untitled and empty, remove
		// (and thus replace) replace it.
		if (getNumDocuments()==1 &&
			currentTextArea.getFileName().equals(owner.getNewFileName()) &&
			currentTextArea.getDocument().getLength()==0 &&
				currentTextArea.isDirty()==false) {
				removeComponentAt(0);
		}

		// If desired, reuse a text area already opened to this file if
		// there is one.
		if (reuse) {
			String fileNameAndPath = loc.getFileFullPath();
			int count = getNumDocuments(); // May have changed from above.
			for (int i=0; i<count; i++) {
				RTextEditorPane textArea = getRTextEditorPaneAt(i);
				if (textArea.getFileFullPath().equals(fileNameAndPath)) {
					setSelectedIndex(i);
					return true;
				}
			}
		}

		String fileFullPath = loc.getFileFullPath();

		// If opening is a local file that exists, or a remote file...
		if (loc.isLocalAndExists() || loc.isRemote()) {

			if (loc.isLocal() && getFileIsTooLarge(fileFullPath)) {
				return false;
			}

			try {
				RTextEditorPane tempTextArea = createRTextEditorPane(
								loc, charSet, delegate);
				addTextArea(tempTextArea);
			} catch (IOException ioe) {
				handleAddTextFileIOException(loc, ioe, true);
				ensureFilesAreOpened();
				return false;
			} catch (OutOfMemoryError oome) {
				owner.displayException(oome);
				ensureFilesAreOpened();
				return false;
			}

			return true;

		} // End of if (loc.isLocalAndExists())

		// If the file is local but doesn't exist...
		else {//if (loc.isLocal()) {
			String temp = owner.getString("FileNECreateItMsg", fileFullPath);
			if (JOptionPane.YES_OPTION == JOptionPane.showConfirmDialog(this, temp,
								owner.getString("ConfDialogTitle"),
								JOptionPane.YES_NO_OPTION)) {
				addNewEmptyFile(fileFullPath, charSet);
				return true;
			}
			ensureFilesAreOpened(); // Keep at least 1 document open.
			return false; // Nothing was opened.
		}

	}


	/**
	 * Adds an already-created text file to this tabbed pane.  This method is
	 * synchronized so it doesn't interfere with the thread checking for files
	 * being modified outside of the editor.
	 *
	 * @param fileNameAndPath The full path and name of the file to add.
	 * @param charSet The encoding to use when reading/writing this file.
	 *        If this value is <code>null</code>, the file is checked for
	 *        Unicode; if it is Unicode, it is opened properly.  If it is not
	 *        Unicode, a system default encoding is used.
	 * @return <code>true</code> if the file was opened (or switched to),
	 *         <code>false</code> otherwise (if the file does not exist and
	 *         the user chose NOT to create it, for example).
	 * @throws InvalidCharSetException If the specified character set is
	 *         invalid.
	 */
	public boolean openFile(String fileNameAndPath, String charSet) {
		return openFile(fileNameAndPath, charSet, null);
	}


	/**
	 * Adds an already-created text file to this tabbed pane.  This method is
	 * synchronized so it doesn't interfere with the thread checking for files
	 * being modified outside of the editor.
	 *
	 * @param fileNameAndPath The full path and name of the file to add.
	 * @param charSet The encoding to use when reading/writing this file.
	 *        If this value is <code>null</code>, the file is checked for
	 *        Unicode; if it is Unicode, it is opened properly.  If it is not
	 *        Unicode, a system default encoding is used.
	 * @param reuse If the file is already open, whether to simply switch
	 *        focus to that old copy (vs. opening a new copy).
	 * @return <code>true</code> if the file was opened (or switched to),
	 *         <code>false</code> otherwise (if the file does not exist and
	 *         the user chose NOT to create it, for example).
	 * @throws InvalidCharSetException If the specified character set is
	 *         invalid.
	 */
	public boolean openFile(String fileNameAndPath, String charSet,
								boolean reuse) {
		return openFile(FileLocation.create(fileNameAndPath),
							charSet, null, reuse);
	}


	/**
	 * Adds an already-created text file to this tabbed pane.  This method is
	 * synchronized so it doesn't interfere with the thread checking for files
	 * being modified outside of the editor.
	 *
	 * @param fileNameAndPath The full path and name of the file to add.
	 * @param charSet The encoding to use when reading/writing this file.
	 *        If this value is <code>null</code>, the file is checked for
	 *        Unicode; if it is Unicode, it is opened properly.  If it is not
	 *        Unicode, a system default encoding is used.
	 * @param delegate If non-null, this object will be used to create the
	 *        new text area.
	 * @return <code>true</code> if the file was opened (or switched to),
	 *         <code>false</code> otherwise (if the file does not exist and
	 *         the user chose NOT to create it, for example).
	 * @throws InvalidCharSetException If the specified character set is
	 *         invalid.
	 */
	public boolean openFile(String fileNameAndPath, String charSet,
							EditorPaneCreatorDelegate delegate) {
		return openFile(FileLocation.create(fileNameAndPath),
							charSet, delegate, false);
	}


	/**
	 * If the current editor is dirty, the user is prompted whether they want
	 * to save it.  If they choose "yes", the file is saved, otherwise it is
	 * not.
	 *
	 * @return <code>JOptionPane.YES_OPTION</code> if the file was saved,
	 *         <code>NO_OPTION</code> if the user chose not to save, and
	 *         <code>CANCEL_OPTION</code> if the user canceled the save
	 *         dialog, or an IO error occurs.
	 */
	protected int promptToSaveBeforeClosingIfDirty() {

		int rc = JOptionPane.YES_OPTION;

		// If the current document has been modified, prompt them to save it.
		if (currentTextArea.isDirty()) {

			String temp = owner.getString("SaveChangesPrompt",
									currentTextArea.getFileName());

			// The prompting dialog.
			rc = JOptionPane.showConfirmDialog(owner, temp,
								owner.getString("ConfDialogTitle"),
								JOptionPane.YES_NO_CANCEL_OPTION);

			switch (rc) {

				// If they decide to save...
				case JOptionPane.YES_OPTION:
					// false on IO error or user cancels saving changes
					if (!saveCurrentFile()) {
						return JOptionPane.CANCEL_OPTION;
					}
					break;

				case JOptionPane.CANCEL_OPTION:
				case JOptionPane.CLOSED_OPTION:
					// If they choose to Cancel (NOT "No" to saving), quit.
					return JOptionPane.CANCEL_OPTION;

			}

		}

		return rc; // Either YES_OPTION or NO_OPTION

	}


	/**
	 * Called whenever a property changes on a component we're listening to.
	 */
	public void propertyChange(PropertyChangeEvent e) {

		String propertyName = e.getPropertyName();

		// If the file's path is changing (must be caused by the file being
		// saved(?))...
		if (propertyName.equals(RTextEditorPane.FULL_PATH_PROPERTY)) {
			setDocumentDisplayNameAt(getSelectedIndex(), currentTextArea.getFileName());
			fireCurrentTextAreaEvent(CurrentTextAreaEvent.FILE_NAME_CHANGED,
								e.getOldValue(), e.getNewValue());
		}

		// If the file's modification status is changing...
		else if (propertyName.equals(RTextEditorPane.DIRTY_PROPERTY)) {
			int selectedIndex = getSelectedIndex();
			String oldTitle = getDocumentDisplayNameAt(selectedIndex);
			if ( ((Boolean)e.getNewValue()).booleanValue()==true )
				setDocumentDisplayNameAt(selectedIndex, oldTitle+"*");
			else {
				setDocumentDisplayNameAt(selectedIndex,
						oldTitle.substring(0,oldTitle.length()-1));	// Get rid of the "*".
			}
			fireCurrentTextAreaEvent(
							CurrentTextAreaEvent.IS_MODIFIED_CHANGED,
							e.getOldValue(), e.getNewValue());
		}

		// If the highlighting style of the current file changed...
		else if (propertyName.equals(RTextEditorPane.SYNTAX_STYLE_PROPERTY)) {
			fireCurrentTextAreaEvent(
						CurrentTextAreaEvent.SYNTAX_STYLE_CNANGED,
						e.getOldValue(), e.getNewValue());
		}

		// If the user changed any search options via a search dialog...
		else if (propertyName.equals(FindDialog.MATCH_CASE_PROPERTY)) {
			searchMatchCase = ((Boolean)e.getNewValue()).booleanValue();
		}
		else if (propertyName.equals(FindDialog.MATCH_WHOLE_WORD_PROPERTY)) {
			searchWholeWord = ((Boolean)e.getNewValue()).booleanValue();
		}
		else if (propertyName.equals(FindDialog.USE_REG_EX_PROPERTY)) {
			searchRegExpression = ((Boolean)e.getNewValue()).booleanValue();
		}
		else if (propertyName.equals(FindDialog.SEARCH_DOWNWARD_PROPERTY)) {
			searchingForward = ((Boolean)e.getNewValue()).booleanValue();
		}
		else if (propertyName.equals(FindDialog.MARK_ALL_PROPERTY)) {
			currentTextArea.clearMarkAllHighlights();
			searchMarkAll = ((Boolean)e.getNewValue()).booleanValue();
		}

		// This is exclusive to the FindInFilesDialog.
		else if (propertyName.equals(FindInFilesDialog.SEARCH_STRINGS_PROPERTY)) {
			searchStrings = (Vector)e.getNewValue();
		}

	}


	/**
	 * Repaints the display names for open documents.
	 */
	public abstract void refreshDisplayNames();


	/**
	 * Updates the icons used by actions owned by this panel to be in synch
	 * with the <code>org.fife.ui.rtextarea.IconGroup</code> used by the
	 * parent <code>RText</code>.
	 */
	public void refreshIcons() {

		IconGroup group = owner.getIconGroup();
		Icon icon = group.getIcon("close");
		actions[CLOSE_ACTION].putValue(Action.SMALL_ICON, icon);
		icon = group.getIcon("find");
		actions[FIND_ACTION].putValue(Action.SMALL_ICON, icon);
		icon = group.getIcon("findnext");
		actions[FIND_NEXT_ACTION].putValue(Action.SMALL_ICON, icon);
		icon = group.getIcon("replace");
		actions[REPLACE_ACTION].putValue(Action.SMALL_ICON, icon);
		icon = group.getIcon("replacenext");
		actions[REPLACE_NEXT_ACTION].putValue(Action.SMALL_ICON, icon);
		icon = group.getIcon("print");
		actions[PRINT_ACTION].putValue(Action.SMALL_ICON, icon);
		icon = group.getIcon("printpreview");
		actions[PRINT_PREVIEW_ACTION].putValue(Action.SMALL_ICON, icon);
		icon = group.getIcon("closeall");
		actions[CLOSE_ALL_ACTION].putValue(Action.SMALL_ICON, icon);

		// Change all RTextAreas' open documents' icon sets.
		RTextEditorPane.setIconGroup(group);

	}


	/**
	 * Looks for duplicate open documents (documents opened more than once)
	 * and adds numbers to the display names for these documents to
	 * differentiate them.
	 */
	public void renumberDisplayNames() {

		// Go through and renumber any tab headings, if necessary.
		int numDocuments = getNumDocuments();
		if (numDocuments>0) {

			boolean[] doneYet = new boolean[numDocuments];

			for (int i=0; i<numDocuments; i++) {
				if (doneYet[i]==true)
					continue;
				RTextEditorPane pane_I = getRTextEditorPaneAt(i);
				String fileFullPath = pane_I.getFileFullPath();
				int count = 1;
				for (int j=i+1; j<numDocuments; j++) {
					RTextEditorPane pane = getRTextEditorPaneAt(j);
					if (!doneYet[j] && pane.getFileFullPath().equals(fileFullPath)) {
						String title = pane.getFileName() + " (" + (++count) + ")";
						if (pane.isDirty()==true)
							title = title + "*";
						setDocumentDisplayNameAt(j, title);
						doneYet[j] = true;
					}
				}
				if (count>1) {
					String title = pane_I.getFileName() + " (1)";
					if (pane_I.isDirty()==true)
						title = title + "*";
					setDocumentDisplayNameAt(i, title);
				}
				else {
					String title = pane_I.getFileName();
					if (pane_I.isDirty()==true)
						title = title + "*";
					setDocumentDisplayNameAt(i, title);
				}
				doneYet[i] = true;
			} // End of for (int i=0; i<numDocuments; i++).

		} // End of if (numDocuments>0).

	}


	/**
	 * Removes a component from this container.
	 *
	 * @param index The index of the component to remove.
	 */
	protected abstract void removeComponentAt(int index);


	/**
	 * Removes a current text area listener.
	 *
	 * @param l The listener to remove.
	 * @see #addCurrentTextAreaListener
	 */
	public void removeCurrentTextAreaListener(CurrentTextAreaListener l) {
		listenerList.remove(CurrentTextAreaListener.class, l);
	}


	/**
	 * Makes all actions use default accelerators.
	 */
	void restoreDefaultAccelerators() {
		int num = RTextPreferences.defaultMainViewActionAccelerators.length;
		for (int i=0; i<num; i++)
			actions[i].putValue(Action.ACCELERATOR_KEY,
				RTextPreferences.defaultMainViewActionAccelerators[i]);
	}


	/**
	 * Attempts to save all currently-opened files.  If any files have unsaved
	 * changes, the user is prompted whether to save them.
	 *
	 * @return Whether all files were successfully saved.  This will be
	 *         <code>false</code> if an IO error occurs, or if a file has
	 *         unsaved changes and the user selects "Cancel" when prompted
	 *         to save them.
	 * @see #saveCurrentFile()
	 * @see #saveCurrentFileAs()
	 * @see #saveCurrentFileAs(FileLocation)
	 */
	public synchronized boolean saveAllFiles() {

		boolean allSaved = true;

		// Remember the number for the tab they are currently working on.
		int currentTab = getSelectedIndex();

		// Cycle through each document, one by one.
		int numDocuments = getNumDocuments();
		for (int i=0; i<numDocuments; i++) {
			// Save this document, if it is not read-only
			if (getRTextEditorPaneAt(i).isReadOnly()==false) {
				setSelectedIndex(i);
				allSaved |= saveCurrentFile();
			}
		}

		// Set the active document to the one originally being working on.
		setSelectedIndex(currentTab);
		return allSaved;

	}


	/**
	 * Attempts to save the currently active file as it's current name.
	 * If the file is named "Untitled.txt", this action is essentially a
	 * "Save As" - the user is then prompted for a name to save with.<p>
	 *
	 * If an IO error occurs, the user is notified.
	 *
	 * @return <code>true</code> if the save is successful, <code>false</code>
	 *         if the user cancels the save operation or an IO error occurs.
	 * @see #saveAllFiles()
	 * @see #saveCurrentFileAs()
	 * @see #saveCurrentFileAs(FileLocation)
	 */
	public synchronized boolean saveCurrentFile() {

		// If this file is named "Untitled.txt", prompt them for a new name.
		if (currentTextArea.getFileName().equals(owner.getNewFileName())) {
			return saveCurrentFileAs();
		}

		// Try and write output to the current filename.
		try {
			currentTextArea.save();
			return true;
		} catch (Exception e) {
			String temp = owner.getString("ErrorWritingFile",
					currentTextArea.getFileFullPath(), e.getMessage());
			JOptionPane.showMessageDialog(this, temp,
				owner.getString("ErrorDialogTitle"), JOptionPane.ERROR_MESSAGE);
			owner.setMessages(null, "ERROR:  Could not save file!");
			return false;
		}

	}


	/**
	 * Attempts to save the currently active file.  The user will be prompted
	 * for a new file name to save with.
	 *
	 * @return <code>true</code> if the save is successful, <code>false</code>
	 *         if the user cancels the save operation or an IO error occurs.
	 * @see #saveCurrentFileAs(FileLocation)
	 * @see #saveCurrentFile()
	 * @see #saveAllFiles()
	 */
	public synchronized boolean saveCurrentFileAs() {

		// Ensures text area gets focus after save for saves that don't bring
		// up an extra window (Save As, etc.).  Without this, the text area
		// would lose focus.
		currentTextArea.requestFocusInWindow();

		// Get the new filename they'd like to use.
		RTextFileChooser chooser = owner.getFileChooser();
		chooser.setMultiSelectionEnabled(false);	// Disable multiple file selection.
		File initialSelection = new File(currentTextArea.getFileFullPath());
		chooser.setSelectedFile(initialSelection);
		chooser.setOpenedFiles(getOpenFiles());
		// Set encoding to what it was read-in or last saved as.
		chooser.setEncoding(currentTextArea.getEncoding());

		int returnVal = chooser.showSaveDialog(owner);

		// If they entered a new filename and clicked "OK", save the flie!
		if(returnVal == RTextFileChooser.APPROVE_OPTION) {

			File chosenFile = chooser.getSelectedFile();
			String chosenFileName = chosenFile.getName();
			String chosenFilePath = chosenFile.getAbsolutePath();
			String encoding = chooser.getEncoding();

			// If the current file filter has an obvious extension
			// associated with it, use it if the specified filename has
			// no extension.  Get the extension from the filter by
			// checking whether the filter is of the form
			// "Foobar Files (*.foo)", and it if is, use the ".foo"
			// extension.
			String extension = chooser.getFileFilter().getDescription();
			int leftParen = extension.indexOf("(*");
			if (leftParen>-1) {
				int start = leftParen + 2; // Skip "(*".
				int end = extension.indexOf(')', start);
				int comma = extension.indexOf(',', start);
				if (comma>-1 && comma<end)
					end = comma;
				if (end>start+1) { // Ensure a ')' or ',' was found.
					extension = extension.substring(start, end);
					// If the file name they entered has no extension,
					// add this extension to it.
					if (chosenFileName.indexOf('.')==-1) {
						chosenFileName = chosenFileName + extension;
						chosenFilePath = chosenFilePath + extension;
						chosenFile = new File(chosenFilePath);
					}
				}
			}

			// If the file already exists, prompt them to see whether
			// or not they want to overwrite it.
			if (chosenFile.exists()) {
				String temp = owner.getString("FileAlreadyExists",
										chosenFile.getName());
				if (JOptionPane.YES_OPTION != JOptionPane.showConfirmDialog(
						this, temp, owner.getString("ConfDialogTitle"),
						JOptionPane.YES_NO_OPTION)) {
					return false;
				}
			}

			// If necessary, change the current file's encoding.
			String oldEncoding = currentTextArea.getEncoding();
			if (encoding!=null && !encoding.equals(oldEncoding))
				currentTextArea.setEncoding(encoding);

			// Try to save the file with a new name.
			return saveCurrentFileAs(FileLocation.create(chosenFilePath));

		} // End of if(returnVal == RTextFileChooser.APPROVE_OPTION).

		// If they cancel the save...
		return false;

	}


	/**
	 * Attempts to save the currently active file with a new name.  If the
	 * save is successful, the current editor is renamed to the new name; if
	 * the save fails, the name doesn't change.
	 *
	 * @param loc The location to save the file to.
	 * @return <code>true</code> if the save is successful, <code>false</code>
	 *         if an IO error occurs.
	 */
	public synchronized boolean saveCurrentFileAs(FileLocation loc) {

		// Try and write output to the current filename.
		try {
			currentTextArea.saveAs(loc);
		} catch (IOException ioe) {
			handleAddTextFileIOException(loc, ioe, false);
			ensureFilesAreOpened();
			return false;
		} catch (OutOfMemoryError oome) {
			owner.displayException(oome);
			ensureFilesAreOpened();
			return false;
		}

		// Decide if we need to update the UI for syntax highlighting
		// purposes (i.e., if the user saves a .txt file as a .java or a
		// .c => .cpp, etc.).
		String newStyle = syntaxFilters.getSyntaxStyleForFile(loc.getFileName());
		setSyntaxStyle(currentTextArea, newStyle);

		// If they had the same file opened twice (i.e., the "foo (1)"
		// and "foo (2)"), and did "Save As..." on one of them, the other
		// needs its number removed.  So we'll renumber filenames.
		renumberDisplayNames();

		return true;

	}


	/**
	 * Sets the alpha value used to make a background image translucent.  Note
	 * that if the background being used is simply a color and not a JPG or GIF
	 * image, this value does nothing.
	 *
	 * @param alpha The new alpha value to use to make background images
	 *        translucent. This value should be between 0.0f and 1.0f.
	 *        If it is less than 0.0f, it will be rounded up to 0.0f; if
	 *        it is greater than 1.0f, it will be rounded down to 1.0f.
	 * @see #getBackgroundImageAlpha
	 */
	public void setBackgroundImageAlpha(float alpha) {
		if (alpha<0.0f)
			alpha = 0.0f;
		else if (alpha>1.0f)
			alpha = 1.0f;
		imageAlpha = alpha;
	}


	/**
	 * Sets the path to the file containing the current background image.
	 * Note that this should only be called in conjunction with
	 * <code>setBackgroundObject</code> when the <code>Object</code> is an
	 * instance of <code>java.awt.Image</code>  If you are setting the
	 * background to a <code>java.awt.Color</code>, you should pass
	 * <code>null</code> to this method.
	 *
	 * @param path The path to the file containing the current background
	 *        image, or <code>null</code> if the current background is an
	 *        image.
	 * @see #getBackgroundImageFileName
	 */
	public void setBackgroundImageFileName(String path) {
		backgroundImageFileName = path;
	}


	/**
	 * Makes the background into this <code>Object</code> (either a
	 * <code>java.awt.Color</code> or a <code>java.awt.Image</code>).
	 *
	 * @param newBackground The <code>java.awt.Color</code> or
	 *        <code>java.awt.Image</code> object.
	 */
	public void setBackgroundObject(Object newBackground) {

		// If they passed in a valid type, remember the object.
		if (newBackground instanceof Color) {
			backgroundObject = newBackground;
		}
		else if (newBackground instanceof Image) {
			backgroundObject = getTranslucentImage(
								(Image)newBackground, imageAlpha);
		}

		// If they didn't pass in a valid type...
		else {
			// Tell them we're defaulting to basic white.
			ResourceBundle msg = owner.getResourceBundle();
			JOptionPane.showMessageDialog(this,
					"Invalid background Object type:\n" + newBackground,
					msg.getString("ErrorDialogTitle"),
					JOptionPane.ERROR_MESSAGE);

			// Default to basic white.
			backgroundObject = Color.WHITE;

		}

		// Now, implement that background.
		int numDocuments = getNumDocuments();
		for (int i=0; i<numDocuments; i++)
			getRTextEditorPaneAt(i).setBackgroundObject(backgroundObject);

	}


	/**
	 * Toggles whether bookmarks are enabled.
	 *
	 * @param enabled Whether bookmarks are enabled.
	 * @see #getBookmarksEnabled()
	 */
	public void setBookmarksEnabled(boolean enabled) {
		if (enabled!=bookmarksEnabled) {
			int docCount = getNumDocuments();
			for (int i=0; i<docCount; i++) {
				Gutter g = getRTextScrollPaneAt(i).getGutter();
				g.setBookmarkingEnabled(enabled);
				//g.setIconRowHeaderVisible(enabled);
			}
			bookmarksEnabled = enabled;
		}
	}


	/**
	 * Sets whether or not bracket matching is enabled.
	 *
	 * @param enabled Whether or not bracket matching should be enabled.
	 * @see #isBracketMatchingEnabled
	 */
	public void setBracketMatchingEnabled(boolean enabled) {
		if (enabled!=bracketMatchingEnabled) {
			bracketMatchingEnabled = enabled;
			int num = getNumDocuments();
			for (int i=0; i<num; i++)
				getRTextEditorPaneAt(i).setBracketMatchingEnabled(
											bracketMatchingEnabled);
		}
	}


	/**
	 * Sets the blink rate for carets in all text areas.
	 *
	 * @param blinkRate The new blink rate.  If this value is invalid (&lt; 0),
	 *        nothing happens.
	 * @see #getCaretBlinkRate
	 */
	public void setCaretBlinkRate(int blinkRate) {
		if (blinkRate>=0 && blinkRate!=caretBlinkRate) {
			caretBlinkRate = blinkRate;
			int count = getNumDocuments();
			for (int i=0; i<count; i++) {
				Caret c = getRTextEditorPaneAt(i).getCaret();
				if (c!=null)
					c.setBlinkRate(caretBlinkRate);
			}
		}
	}


	/**
	 * Sets the color of the caret used by all text areas in this tabbed pane.
	 *
	 * @param color The color to use for the caret.  If <code>null</code> is
	 *        passed in, there is no change to the caret color being used.
	 */
	public void setCaretColor(final Color color) {
		if (color!=null && color!=caretColor) {
			caretColor = color;
			int numDocuments = getNumDocuments();
			for (int i=0; i<numDocuments; i++)
				getRTextEditorPaneAt(i).setCaretColor(color);
		}
	}


	/**
	 * Sets the caret style for either the insert or overwrite caret.
	 *
	 * @param mode Either <code>RTextArea.INSERT_MODE</code> or
	 *        <code>RTextArea.OVERWRITE_MODE</code>.
	 * @param style The style for the specified caret, such as
	 *        <code>ConfigurableCaret.VERTICAL_LINE_STYLE</code>.
	 * @see #getCaretStyle
	 */
	public void setCaretStyle(int mode, int style) {
		if (mode!=RTextArea.INSERT_MODE &&
				mode!=RTextArea.OVERWRITE_MODE)
			return;
		if (carets[mode]!=style) {
			carets[mode] = style;
			int numDocuments = getNumDocuments();
			for (int i=0; i<numDocuments; i++)
				getRTextEditorPaneAt(i).setCaretStyle(mode, style);
		}
	}


	/**
	 * Sets the color to use to highlight the current line.  Note that if
	 * highlighting the current line is turned off, you will not be able to see
	 * this highlight.
	 *
	 * @param color The color to use to highlight the current line.
	 * @throws NullPointerException if <code>color</code> is <code>null</code>.
	 * @see #isCurrentLineHighlightEnabled
	 * @see #setCurrentLineHighlightEnabled
	 * @see #getCurrentLineHighlightColor
	 */
	public void setCurrentLineHighlightColor(Color color) {
		if (color==null)
			throw new NullPointerException();
		currentLineColor = color;
		int numDocuments = getNumDocuments();
		for (int i=0; i<numDocuments; i++) {
			RTextEditorPane textArea = getRTextEditorPaneAt(i);
			textArea.setCurrentLineHighlightColor(currentLineColor);
		}
	}


	/**
	 * Sets whether or not the current line is highlighted.
	 *
	 * @param enabled Whether or not to highlight the current line.
	 * @see #isCurrentLineHighlightEnabled
	 * @see #getCurrentLineHighlightColor
	 * @see #setCurrentLineHighlightColor
	 */
	public void setCurrentLineHighlightEnabled(boolean enabled) {
		highlightCurrentLine = enabled;
		int numDocuments = getNumDocuments();
		for (int i=0; i<numDocuments; i++) {
			RTextEditorPane textArea = getRTextEditorPaneAt(i);
			textArea.setHighlightCurrentLine(highlightCurrentLine);
		}
	}


	/**
	 * Sets the "currently active" text area.  This should only be called
	 * by subclasses.  After this is called, subclasses should call
	 * {@link #fireCurrentTextAreaEvent(int, Object, Object)} to notify any
	 * registered listeners of the change.
	 *
	 * @param textArea The new text area.
	 * @see #getCurrentTextArea()
	 */
	/*
	 * TODO: Make this method fire the event.
	 */
	protected void setCurrentTextArea(RTextEditorPane textArea) {
		currentTextArea = textArea;
	}


	/**
	 * Sets the default encoding of new text files to the specified value.
	 * This method fires a property change event of type
	 * {@link #DEFAULT_ENCODING_PROPERTY}.
	 *
	 * @param encoding The new default encoding.  A value of <code>null</code>
	 *        means to use the system default encoding.
	 * @see #getDefaultEncoding()
	 */
	public void setDefaultEncoding(String encoding) {
		if ((defaultEncoding!=null && !defaultEncoding.equals(encoding)) ||
				(defaultEncoding==null && encoding!=null)) {
			String old = this.defaultEncoding;
			this.defaultEncoding = encoding;
			firePropertyChange(DEFAULT_ENCODING_PROPERTY, old, encoding);
		}
	}


	/**
	 * Sets the name being displayed for a given document.  For example, in a
	 * tabbed pane subclass implementation, this could be the name on the tab
	 * of the document.
	 *
	 * @param index The index for the document for which to set the short name.
	 *        If <code>index</code> is invalid, this method does nothing.
	 * @param displayName The name to display.
	 * @see #getDocumentDisplayNameAt
	 */
	public abstract void setDocumentDisplayNameAt(int index, String displayName);


	/**
	 * Changes the location of the document selection area of this component.
	 *
	 * @param location The location to use; (<code>TOP</code>,
	 *        <code>LEFT</code>, <code>BOTTOM</code>, or <code>RIGHT</code>.
	 *        If this value is invalid, nothing happens.
	 */
	 public abstract void setDocumentSelectionPlacement(int location);


	/**
	 * Sets whether a file's size is checked before it is opened.  This
	 * method fires a property change event of type
	 * {@link #FILE_SIZE_CHECK_PROPERTY}.
	 *
	 * @param doCheck Whether to check a file's size.
	 * @see #getDoFileSizeCheck()
	 */
	public void setDoFileSizeCheck(boolean doCheck) {
		if (doCheck!=doFileSizeCheck) {
			doFileSizeCheck = doCheck;
			firePropertyChange(FILE_SIZE_CHECK_PROPERTY, !doCheck, doCheck);
		}
	}


	/**
	 * Sets whether fractional fontmetrics is enabled. This method fires a
	 * property change of type {@link #FRACTIONAL_METRICS_PROPERTY}.
	 *
	 * @param enabled Whether fractional fontmetrics should be enabled.
	 * @see #isFractionalFontMetricsEnabled
	 */
	public void setFractionalFontMetricsEnabled(boolean enabled) {
		if (fractionalMetricsEnabled!=enabled) {
			fractionalMetricsEnabled = enabled;
			int count = getNumDocuments();
			for (int i=0; i<count; i++)
				getRTextEditorPaneAt(i).setFractionalFontMetricsEnabled(
														enabled);
			firePropertyChange(FRACTIONAL_METRICS_PROPERTY,
												!enabled, enabled);
		}
	}


	/**
	 * Sets whether files with no extension have their content type guessed
	 * at via whether they have a "<code>#!</code>" in their first line.
	 *
	 * @param guess Whether to guess the content type of files with no
	 *        extension.
	 * @see #getGuessFileContentType()
	 */
	public void setGuessFileContentType(boolean guess) {
		if (guess!=guessFileContentType) {
			guessFileContentType = guess;
			int docCount = getNumDocuments();
			for (int i=0; i<docCount; i++) {
				RTextEditorPane textArea = getRTextEditorPaneAt(i);
				String style = syntaxFilters.getSyntaxStyleForFile(
											textArea.getFileName());
				setSyntaxStyle(textArea, style);
			}
		}
	}

	/**
	 * Sets the color to use for the gutter's border.
	 *
	 * @param c The new gutter border color.  This cannot be <code>null</code>.
	 * @see #getGutterBorderColor()
	 */
	public void setGutterBorderColor(Color c) {
		if (c!=null && !c.equals(gutterBorderColor)) {
			int docCount = getNumDocuments();
			for (int i=0; i<docCount; i++) {
				Gutter g = getRTextScrollPaneAt(i).getGutter();
				g.setBorderColor(c);
			}
			gutterBorderColor = c;
		}
	}


	/**
	 * Sets whether this panel will highlight modified documents' display
	 * names with a different color.
	 *
	 * @param highlight Whether or not to highlight modified documents' display
	 *        names.
	 * @see #highlightModifiedDocumentDisplayNames
	 * @see #getModifiedDocumentDisplayNamesColor
	 * @see #setModifiedDocumentDisplayNamesColor
	 */
	public void setHighlightModifiedDocumentDisplayNames(boolean highlight) {
		if (highlight!=highlightModifiedDocDisplayNames) {
			highlightModifiedDocDisplayNames = highlight;
			refreshDisplayNames();
		}
	}


	/**
	 * Sets the color hyperlinks are displayed with in text editors.
	 *
	 * @param c The color to display.  This cannot be <code>null</code>.
	 * @see #getHyperlinkColor()
	 */
	public void setHyperlinkColor(Color c) {
		if (c!=null && !c.equals(getHyperlinkColor())) {
			this.hyperlinkColor = c;
			int docCount = getNumDocuments();
			for (int i=0; i<docCount; i++) {
				getRTextEditorPaneAt(i).setHyperlinkForeground(c);
			}
		}
	}


	/**
	 * Sets the modifier key used to start hyperlink scanning in text
	 * editors.
	 *
	 * @param key The modifier key(s).  If an invalid value is passed in,
	 *        {@link java.awt.event.InputEvent#CTRL_DOWN_MASK} is used.
	 * @see #getHyperlinkModifierKey()
	 * @see java.awt.event.InputEvent
	 */
	public void setHyperlinkModifierKey(int key) {
		switch (key) {
			case InputEvent.CTRL_DOWN_MASK:
			case InputEvent.META_DOWN_MASK:
			case InputEvent.SHIFT_DOWN_MASK:
			case InputEvent.ALT_DOWN_MASK:
				break;
			default: // Prevent invalid values.
				key = InputEvent.CTRL_DOWN_MASK;
		}
		if (key!=hyperlinkModifierKey) {
			hyperlinkModifierKey = key;
			int docCount = getNumDocuments();
			for (int i=0; i<docCount; i++) {
				getRTextEditorPaneAt(i).setLinkScanningMask(key);
			}
		}
	}


	/**
	 * Sets whether hyperlinks are enabled in text editors.
	 *
	 * @param enabled Whether hyperlinks should be enabled.
	 * @see #getHyperlinksEnabled()
	 */
	public void setHyperlinksEnabled(boolean enabled) {
		if (enabled!=hyperlinksEnabled) {
			hyperlinksEnabled = enabled;
			int numDocuments = getNumDocuments();
			for (int i=0; i<numDocuments; i++) {
				getRTextEditorPaneAt(i).setHyperlinksEnabled(enabled);
			}
		}
	}


	/**
	 * Sets the color used for line numbers.
	 *
	 * @param c The new line number color.  This cannot be <code>null</code>.
	 * @see #getLineNumberColor()
	 */
	public void setLineNumberColor(Color c) {
		if (c!=null && !c.equals(lineNumberColor)) {
			int docCount = getNumDocuments();
			for (int i=0; i<docCount; i++) {
				Gutter g = getRTextScrollPaneAt(i).getGutter();
				g.setLineNumberColor(c);
			}
			lineNumberColor = c;
		}
	}


	/**
	 * Sets the font used for line numbers.
	 *
	 * @param f The new line number font.  This cannot be <code>null</code>.
	 * @see #getLineNumberFont()
	 */
	public void setLineNumberFont(Font f) {
		if (f!=null && !f.equals(lineNumberFont)) {
			int docCount = getNumDocuments();
			for (int i=0; i<docCount; i++) {
				Gutter g = getRTextScrollPaneAt(i).getGutter();
				g.setLineNumberFont(f);
			}
			lineNumberFont = f;
		}
	}


	/**
	 * Enables/disables the line numbers for the open documents.
	 *
	 * @param enabled Whether or not line numbers should be enabled.
	 */
	public void setLineNumbersEnabled(boolean enabled) {
		if (enabled!=lineNumbersEnabled) {
			lineNumbersEnabled = enabled;
			int numDocuments = getNumDocuments();
			for (int i=0; i<numDocuments; i++) {
				getRTextScrollPaneAt(i).setLineNumbersEnabled(enabled);
			}
		}
	}


	/**
	 * Sets the line terminator to use for new text files.
	 *
	 * @param terminator The line terminator to use.
	 * @see #getLineTerminator()
	 */
	public void setLineTerminator(String terminator) {
		if (terminator!=null &&
				terminator.equals(System.getProperty("line.separator"))) {
			terminator = null;
		}
		defaultLineTerminator = terminator;
	}


	/**
	 * Enables/disables word wrap of the open documents.
	 *
	 * @param enabled Whether or not word wrap should be enabled.
	 */
	public void setLineWrap(boolean enabled) {
		if (enabled!=lineWrapEnabled) {
			lineWrapEnabled = enabled;
			int numDocuments = getNumDocuments();
			for (int i=0; i<numDocuments; i++)
				getRTextEditorPaneAt(i).setLineWrap(enabled);
		}
	}


	/**
	 * Sets the color used for the margin line in text areas.  If this value
	 * is the same as the current value, nothing happens.
	 *
	 * @param color The new color to use.
	 * @see #getMarginLineColor
	 */
	public void setMarginLineColor(Color color) {
		if (!color.equals(marginLineColor)) {
			marginLineColor = color;
			int numDocuments = getNumDocuments();
			for (int i=0; i<numDocuments; i++)
				getRTextEditorPaneAt(i).setMarginLineColor(marginLineColor);
		}
	}


	/**
	 * Sets whether or not the margin line is enabled in all text areas.  If
	 * this value is the same as the current value, nothing happens.
	 *
	 * @param enabled Whether or not the margin line should be enabled.
	 * @see #isMarginLineEnabled
	 */
	public void setMarginLineEnabled(boolean enabled) {
		if (marginLineEnabled != enabled) {
			marginLineEnabled = enabled;
			int numDocuments = getNumDocuments();
			for (int i=0; i<numDocuments; i++)
				getRTextEditorPaneAt(i).setMarginLineEnabled(enabled);
		}
	}


	/**
	 * Sets the margin line position in all text areas.  If this value is
	 * the same as the current value, nothing happens.
	 *
	 * @param position The new margin line position.
	 * @see #getMarginLinePosition
	 */
	public void setMarginLinePosition(int position) {
		if (marginLinePosition!=position) {
			marginLinePosition = position;
			int numDocuments = getNumDocuments();
			for (int i=0; i<numDocuments; i++)
				getRTextEditorPaneAt(i).setMarginLinePosition(marginLinePosition);
		}
	}


	/**
	 * Sets the color selected for "mark all."  This method fires a property
	 * change of type {@link #MARK_ALL_COLOR_PROPERTY}.
	 *
	 * @param color The color to have selected.
	 * @see #getMarkAllHighlightColor
	 */
	public void setMarkAllHighlightColor(Color color) {
		if (color!=null && !color.equals(markAllHighlightColor)) {
			int count = getNumDocuments();
			for (int i=0; i<count; i++)
				getRTextEditorPaneAt(i).setMarkAllHighlightColor(color);
			Color oldColor = markAllHighlightColor;
			markAllHighlightColor = color;
			firePropertyChange(MARK_ALL_COLOR_PROPERTY, oldColor, color);
		}
	}


	/**
	 * Sets whether "mark occurrences" is enabled.  This method fires a
	 * property change event of type {@link #MARK_OCCURRENCES_PROPERTY}.
	 *
	 * @param markOccurrences Whether "mark occurrences" should be enabled.
	 * @see #getMarkOccurrences()
	 */
	public void setMarkOccurrences(boolean markOccurrences) {
		if (markOccurrences!=this.markOccurrences) {
			this.markOccurrences = markOccurrences;
			int count = getNumDocuments();
			for (int i=0; i<count; i++) {
				getRTextEditorPaneAt(i).setMarkOccurrences(markOccurrences);
			}
			firePropertyChange(MARK_OCCURRENCES_PROPERTY,
							!markOccurrences, markOccurrences);
		}
	}


	/**
	 * Sets the color to use to "mark occurrences."  This method fires a
	 * property change event of type {@link #MARK_OCCURRENCES_COLOR_PROPERTY}.
	 *
	 * @param color The color.
	 * @see #getMarkOccurrencesColor()
	 * @see #setMarkOccurrences(boolean)
	 */
	public void setMarkOccurrencesColor(Color color) {
		if (color!=null && markOccurrencesColor!=color) {
			Color old = markOccurrencesColor;
			markOccurrencesColor = color;
			int count = getNumDocuments();
			for (int i=0; i<count; i++) {
				getRTextEditorPaneAt(i).setMarkOccurrencesColor(color);
			}
			firePropertyChange(MARK_OCCURRENCES_COLOR_PROPERTY, old, color);
		}
	}


	/**
	 * Sets the background color to use in bracket matching.
	 *
	 * @param color The background color to use when highlighting a bracket.
	 * @see #getMatchedBracketBGColor
	 */
	public void setMatchedBracketBGColor(Color color) {
		if (color!=matchedBracketBGColor) {
			matchedBracketBGColor = color;
			int num = getNumDocuments();
			for (int i=0; i<num; i++) {
				RTextEditorPane textArea = getRTextEditorPaneAt(i);
				// Will repaint if necessary.
				textArea.setMatchedBracketBGColor(matchedBracketBGColor);
			}
		}
	}


	/**
	 * Sets the border color to use in bracket matching.
	 *
	 * @param color The border color to use when highlighting a bracket.
	 * @see #getMatchedBracketBorderColor
	 */
	public void setMatchedBracketBorderColor(Color color) {
		if (color!=matchedBracketBorderColor) {
			matchedBracketBorderColor = color;
			int num = getNumDocuments();
			for (int i=0; i<num; i++) {
				RTextEditorPane textArea = getRTextEditorPaneAt(i);
				// Will repaint if necessary.
				textArea.setMatchedBracketBorderColor(matchedBracketBorderColor);
			}
		}
	}


	/**
	 * If file size checking is enabled, this is the maximum size a file
	 * can be before the user is prompted before opening it.  This
	 * method fires a property change event of type
	 * {@link #MAX_FILE_SIZE_PROPERTY}.
	 *
	 * @param size The new maximum size for a file before the user is
	 *        prompted before opening it.
	 * @see #getMaxFileSize()
	 * @see #setDoFileSizeCheck(boolean)
	 */
	public void setMaxFileSize(float size) {
		if (maxFileSize!=size) {
			float old = maxFileSize;
			maxFileSize = size;
			firePropertyChange(MAX_FILE_SIZE_PROPERTY, old, maxFileSize);
		}
	}


	/**
	 * Sets the color used to highlight modified documents' display names.
	 *
	 * @param color The color to use.
	 * @throws NullPointerException If <code>color</code> is <code>null</code>.
	 * @see #getModifiedDocumentDisplayNamesColor
	 * @see #highlightModifiedDocumentDisplayNames
	 * @see #setHighlightModifiedDocumentDisplayNames
	 */
	public void setModifiedDocumentDisplayNamesColor(Color color) {
		if (color==null)
			throw new NullPointerException();
		modifiedDocumentDisplayNameColor = color;
		refreshDisplayNames();	// So the color change takes effect.
	}


	/**
	 * Sets the font to use when printing documents.
	 *
	 * @param newPrintFont The font to use when printing documents.  If
	 *        <code>null</code>, then the current RText font will be used.
	 * @see #getPrintFont
	 */
	public void setPrintFont(Font newPrintFont) {
		printFont = newPrintFont;
	}


	/**
	 * This method is called by {@link BeginRecordingMacroAction} and
	 * {@link EndRecordingMacroAction} so we can change the cursor used by all
	 * open text areas when a macro is being recorded.
	 *
	 * @param recording Whether a macro is being recorded.
	 */
	void setRecordingMacro(boolean recording) {
		Cursor cursor = (recording ? getMacroCursor() :
					Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
		int numDocuments = getNumDocuments();
		for (int i=0; i<numDocuments; i++) {
			RTextEditorPane textArea = getRTextEditorPaneAt(i);
			textArea.setCursor(cursor);
		}
	}


	/**
	 * Sets whether selection edges are rounded in text areas.
	 *
	 * @param rounded Whether selection edges are to be rounded.
	 * @see #getRoundedSelectionEdges
	 */
	public void setRoundedSelectionEdges(boolean rounded) {
		if (rounded!=roundedSelectionEdges) {
			roundedSelectionEdges = rounded;
			int count = getNumDocuments();
			for (int i=0; i<count; i++) {
				RTextEditorPane textArea = getRTextEditorPaneAt(i);
				textArea.setRoundedSelectionEdges(rounded);
			}
			firePropertyChange(ROUNDED_SELECTION_PROPERTY,
							!rounded, rounded);
		}
	}


	/**
	 * Sets the currently active document.
	 *
	 * @param index The index of the document to make the active document.
	 *        If this value is invalid, nothing happens.
	 */
	public abstract void setSelectedIndex(int index);


	/**
	 * Sets the currently active text area.
	 *
	 * @param textArea The text area to make active.
	 * @return Whether the text area was made active.  This will be
	 *         <code>false</code> if the text area is not contained in this
	 *         view.
	 * @see #setSelectedIndex(int)
	 */
	public boolean setSelectedTextArea(RTextEditorPane textArea) {
		for (int i=0; i<getNumDocuments(); i++) {
			RTextEditorPane ta2 = getRTextEditorPaneAt(i);
			if (ta2!=null && ta2==textArea) {
				setSelectedIndex(i);
				return true;
			}
		}
		return false;
	}


	/**
	 * Sets the color of selections in all text areas in this tabbed pane.
	 *
	 * @param color The color to use for the selections.  If <code>null</code>
	 *        is passed in, there is no change to the selection color being
	 *        used.
	 */
	public void setSelectionColor(final Color color) {
		if (color!=null && color!=selectionColor) {
			selectionColor = color;
			int numDocuments = getNumDocuments();
			for (int i=0; i<numDocuments; i++)
				getRTextEditorPaneAt(i).setSelectionColor(color);
		}
	}


	/**
	 * Sets the file filters used when opening documents to decide how to
	 * syntax highlight documents.  All currently open text files have their
	 * color schemes updated, if necessary.
	 *
	 * @param syntaxFilters The filter set to use.
	 * @see #getSyntaxFilters
	 */
	public void setSyntaxFilters(SyntaxFilters syntaxFilters) {

		this.syntaxFilters = syntaxFilters;

		// Reset all open files' color schemes if necessary.
		int numDocuments = getNumDocuments();
		for (int i=0; i<numDocuments; i++) {
			RTextEditorPane textArea = getRTextEditorPaneAt(i);
			String oldStyle = textArea.getSyntaxEditingStyle();
			String newStyle = syntaxFilters.getSyntaxStyleForFile(
											textArea.getFileName());
			if (!oldStyle.equals(newStyle)) {
				setSyntaxStyle(textArea, newStyle);
				if (textArea==currentTextArea) {
					textArea.repaint();
				}
			}
		}

	}


	/**
	 * Sets the syntax highlighting color scheme being used.
	 *
	 * @param colorScheme The new color scheme to use.  If <code>null</code>,
	 *        the default color scheme is set.
	 */
	public void setSyntaxScheme(SyntaxScheme colorScheme) {
		if (currentTextArea==null) {
			addNewEmptyUntitledFile();
		}
		int numDocuments = getNumDocuments();
		if (colorScheme!=null) {
			for (int i=0; i<numDocuments; i++) {
				getRTextEditorPaneAt(i).setSyntaxScheme(colorScheme);
			}
		}
		else {
			for (int i=0; i<numDocuments; i++) {
				getRTextEditorPaneAt(i).restoreDefaultSyntaxScheme();
			}
		}
	}


	/**
	 * Sets the syntax style on a text area, guessing it based on its content
	 * if necessary.
	 *
	 * @param pane The text area.
	 * @param style The style for the text area.
	 */
	private void setSyntaxStyle(RTextEditorPane pane, String style) {

		// Remove the old language support.
		LanguageSupportFactory lsf = LanguageSupportFactory.get();
		LanguageSupport support = lsf.getSupport(pane.getSyntaxEditingStyle());
		if (support!=null) {
			support.uninstall(pane);
		}

		// If there was no extension on the file name, guess the content
		// type for highlighting (but don't override content type if already
		// assigned, e.g. "makefile" does this).
		if (getGuessFileContentType() &&
				pane.getFileName().indexOf('.')==-1 &&
				SyntaxConstants.SYNTAX_STYLE_NONE.equals(style)) {
			guessContentType(pane);
		}

		else {
			// Doesn't change style if it's already being used.
			pane.setSyntaxEditingStyle(style);
		}

		// Add the new support.
		support = lsf.getSupport(pane.getSyntaxEditingStyle());
		if (support!=null) {
			support.install(pane);
		}

	}


	/**
	 * Changes whether or not tabs should be emulated with spaces (i.e., soft
	 * tabs).
	 *
	 * @param areEmulated Whether or not tabs should be emulated with spaces.
	 */
	public void setTabsEmulated(boolean areEmulated) {
		if (areEmulated!=emulateTabsWithWhitespace) {
			emulateTabsWithWhitespace = areEmulated;
			int numDocuments = getNumDocuments();
			for (int i=0; i<numDocuments; i++)
				getRTextEditorPaneAt(i).setTabsEmulated(areEmulated);
		}
	}


	/**
	 * Sets the tab size to be used on all documents.
	 *
	 * @param newSize The tab size to use.
	 */
	public void setTabSize(int newSize) {

		// Do nothing if the tab size is invalid (SHOULD THROW AN EXCEPTION).
		if (newSize<0)
			//throw new TabSizeException();
			return;

		// If the new tab size is different from the current one...
		if (newSize!=tabSize) {
			tabSize = newSize;
			int numDocuments = getNumDocuments();
			for (int i=0; i<numDocuments; i++)
				getRTextEditorPaneAt(i).setTabSize(newSize);

		}

	}


	/**
	 * Sets the rendering hint to use when anti-aliasing text in the text
	 * editors.  This method fires a property change of type
	 * {@link #SMOOTH_TEXT_PROPERTY}.
	 *
	 * @param aaHintFieldName The name of a field in
	 *        <code>java.awt.RenderingHints</code>.  The name of the hint is
	 *        used so that, if new values are added in the future, they can
	 *        be used.  Invalid/unsupported values default to
	 *        <code>null</code>.  A <code>null</code> value indicates that
	 *        no text anti-aliasing should be done.
	 * @see #getTextAAHintName()
	 */
	public void setTextAAHintName(String aaHintFieldName) {

		String old = this.aaHintFieldName;

		// If they are setting the AA hint to null (no AA)...
		if (aaHintFieldName==null && old!=null) {
			this.aaHintFieldName = aaHintFieldName;
			int count = getNumDocuments();
			for (int i=0; i<count; i++) {
				getRTextEditorPaneAt(i).setTextAntiAliasHint(
										this.aaHintFieldName);
			}
			firePropertyChange(SMOOTH_TEXT_PROPERTY,
							old, this.aaHintFieldName);
		}

		else if (aaHintFieldName!=null && !aaHintFieldName.equals(old)) {
			this.aaHintFieldName = aaHintFieldName;
			int count = getNumDocuments();
			for (int i=0; i<count; i++) {
				getRTextEditorPaneAt(i).setTextAntiAliasHint(
											aaHintFieldName);
			}
			firePropertyChange(SMOOTH_TEXT_PROPERTY,
							old, this.aaHintFieldName);
		}

	}


	/**
	 * Sets the default font for text areas.
	 *
	 * @param font The font.
	 * @param underline Whether the font is underlined.
	 * @see #getTextAreaFont()
	 * @see #getTextAreaUnderline()
	 */
	public void setTextAreaFont(Font font, boolean underline) {

		if (font==null) {
			font = new Font("Monospaced", Font.PLAIN, 13);
		}

		if (!font.equals(textAreaFont) || underline!=textAreaUnderline) {
			int docCount = getNumDocuments();
			for (int i=0; i<docCount; i++) {
				RTextEditorPane textArea = getRTextEditorPaneAt(i);
				textArea.setFont(font);
				//textArea.setUnderline(underline);
			}
		}

		// Always set our values so text areas created later have them.
		textAreaFont = font;
		textAreaUnderline = underline;

	}


	/**
	 * Sets the default foreground color for text areas.
	 *
	 * @param fg The new default foreground color.
	 * @see #getTextAreaForeground()
	 */
	public void setTextAreaForeground(Color fg) {
		if (fg!=null && !fg.equals(textAreaForeground)) {
			textAreaForeground = fg;
			int count = getNumDocuments();
			for (int i=0; i<count; i++) {
				RTextEditorPane textArea = getRTextEditorPaneAt(i);
				textArea.setForeground(textAreaForeground);
			}
		}
	}


	/**
	 * Sets the component orientation of the text areas.
	 *
	 * @param o The new orientation.
	 * @see #getTextAreaOrientation()
	 */
	public void setTextAreaOrientation(ComponentOrientation o) {
		if (o==null) {
			o = ComponentOrientation.LEFT_TO_RIGHT;
		}
		if (textAreaOrientation==null ||
				o.isLeftToRight()!=textAreaOrientation.isLeftToRight()) {
			textAreaOrientation = o;
			int count = getNumDocuments();
			for (int i=0; i<count; i++) {
				RTextEditorPane textArea = getRTextEditorPaneAt(i);
				textArea.applyComponentOrientation(textAreaOrientation);
			}
		}
	}


	/**
	 * Enables either insert mode or overwrite mode for the text documents.
	 *
	 * @param mode Either <code>RTextEditorPane.INSERT_MODE</code> or
	 *        <code>RTextEditorPane.OVERWRITE_MODE</code>.
	 * @throws IllegalArgumentException If <code>mode</code> is invalid.
	 * @see #getTextMode
	 */
	public void setTextMode(int mode) {
		if (mode!=RTextEditorPane.INSERT_MODE &&
				mode!=RTextEditorPane.OVERWRITE_MODE) {
			throw new IllegalArgumentException("Invalid mode: " + mode);
		}
		textMode = mode;
		int numDocuments = getNumDocuments();
		int selectedIndex = getSelectedIndex();
		for (int i=0; i<numDocuments; i++) {
			// Skip the current text area as it updates its own text mode.
			if (i!=selectedIndex) {
				getRTextEditorPaneAt(i).setTextMode(mode);
			}
		}

	}


	/**
	 * Sets whether whitespace is visible in all open text areas.<p>
	 * This method will not change anything if the value of
	 * <code>visible</code> is already the whitespace-visibility state.
	 *
	 * @param visible Whether whitespace should be visible.
	 */
	public void setWhitespaceVisible(boolean visible) {
		if (whitespaceVisible != visible) {
			whitespaceVisible = visible;
			int count = getNumDocuments();
			for (int i=0; i<count; i++)
				getRTextEditorPaneAt(i).setWhitespaceVisible(visible);
		}
	}


	/**
	 * Sets whether BOM's should be written for UTF-8 files.
	 *
	 * @param write Whether to write BOM's for UTF-8 files.
	 * @see #getWriteBOMInUtf8Files()
	 */
	public void setWriteBOMInUtf8Files(boolean write) {
		System.setProperty(UnicodeWriter.PROPERTY_WRITE_UTF8_BOM,
						Boolean.toString(write));
	}


	/**
	 * Updates the look and feel of objects that the parent <code>RText</code>
	 * can't get to.  This should be called whenever the look and feel is
	 * changed while <code>RText</code> is running.
	 */
	public void updateLookAndFeel() {

		// Remember, the find and replace dialogs are created together.
		if (findDialog != null) {
			SwingUtilities.updateComponentTreeUI(findDialog);
			// Unique to findDialog, NOT all JDialogs.
			findDialog.updateUI();
			findDialog.pack();
			SwingUtilities.updateComponentTreeUI(replaceDialog);
			// Also unique to replaceDialog, NOT all JDialogs.
			replaceDialog.updateUI();
			replaceDialog.pack();
		}

		// Update the GoTo dialog, if it has been created yet.
		if (goToDialog != null) {
			SwingUtilities.updateComponentTreeUI(goToDialog);
			goToDialog.pack();
		}

		// Update the Find In Files dialog, if it exists.
		if (findInFilesDialog != null) {
			SwingUtilities.updateComponentTreeUI(findInFilesDialog);
			// Also unique to findInFilesDialog, NOT all JDialogs.
			findInFilesDialog.updateUI();
			findInFilesDialog.pack();
		}

		// Update all open files to ensure that they keep the "correct"
		// background.  We need to do this because in RText's
		// updateLookAndFeel(), each text area's updateUI() is called, which
		// resets their background to white, evidently.
		int numDocuments = getNumDocuments();
		for (int i=0; i<numDocuments; i++)
			getRTextEditorPaneAt(i).setBackgroundObject(backgroundObject);
		if (currentTextArea != null)
			currentTextArea.repaint();

	}


	/**
	 * Updates the status bar's read-only indicator and line/column indicator.
	 * This should be called whenever the currently active document changes.
	 */
	protected void updateStatusBar() {
		StatusBar statusBar = (StatusBar)owner.getStatusBar();
		if (statusBar!=null) {
			statusBar.setReadOnlyIndicatorEnabled(currentTextArea.isReadOnly());
			int lineNumber = currentTextArea.getCaretLineNumber();
			int lineStartOffset = currentTextArea.getLineStartOffsetOfCurrentLine();
			statusBar.setRowAndColumn(lineNumber+1,
					currentTextArea.getCaretPosition()-lineStartOffset+1);
		}
	}


}