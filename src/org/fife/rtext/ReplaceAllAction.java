/*
 * 11/14/2003
 *
 * ReplaceAllAction.java - Action to replace all occurences of a given string
 * with a new string in RText.
 * Copyright (C) 2003 Robert Futrell
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

import java.awt.event.ActionEvent;
import java.util.ResourceBundle;
import java.util.regex.PatternSyntaxException;
import javax.swing.Icon;
import javax.swing.JOptionPane;

import org.fife.ui.app.StandardAction;
import org.fife.ui.rtextarea.SearchEngine;


/**
 * Action used by an <code>AbstractMainView</code> to replace all occurrences
 * of a given text string with new text.
 *
 * @author Robert Futrell
 * @version 1.0
 */
class ReplaceAllAction extends StandardAction {


	/**
	 * Constructor.
	 *
	 * @param owner The parent RText instance.
	 * @param msg The resource bundle to use for localization.
	 * @param icon The icon associated with the action.
	 */
	public ReplaceAllAction(RText owner, ResourceBundle msg, Icon icon) {
		super(owner, msg, "ReplaceAllAction");
		setIcon(icon);
	}


	/**
	 * Callback routine called when user uses this component.
	 *
	 * @param e The action event.
	 */
	public void actionPerformed(ActionEvent e) {

		RText rtext = (RText)getApplication();
		AbstractMainView mainView = rtext.getMainView();
		RTextEditorPane textArea = mainView.getCurrentTextArea();

		// First, remember that they've searched for this string.
		mainView.searchStrings = mainView.replaceDialog.getSearchStrings();

		// Next, initialize some variables for this action.
		String searchString = mainView.replaceDialog.getSearchString();
		boolean matchCase = mainView.searchMatchCase;
		boolean wholeWord = mainView.searchWholeWord;
		boolean regex = mainView.searchRegExpression;

		// Do the replacement.
		int count = 0;
		try {
			count = SearchEngine.replaceAll(textArea, searchString,
							mainView.replaceDialog.getReplaceString(),
							matchCase, wholeWord, regex);

			if (count==-1) {
				// TODO: Display an error about bad regex.
			}
			else if (count>0) {
				String temp = rtext.getString("ReplacedNOccString",
							Integer.toString(count), searchString);
				JOptionPane.showMessageDialog(rtext, temp,
								rtext.getString("InfoDialogHeader"),
								JOptionPane.INFORMATION_MESSAGE);
			}
			else { // count==0.
				searchString = RTextUtilities.escapeForHTML(searchString, null);
				String temp = rtext.getString("CannotFindString", searchString);
				// "null" parent returns focus to previously focused window,
				// whether it be RText, the Find dialog or the Replace dialog.
				JOptionPane.showMessageDialog(null, temp,
								rtext.getString("InfoDialogHeader"),
								JOptionPane.INFORMATION_MESSAGE);
			}

		} catch (PatternSyntaxException pse) {
			// There was a problem with the user's regex search string.
			// Won't usually happen; should be caught earlier.
			JOptionPane.showMessageDialog(rtext,
			"Invalid regular expression:\n" + pse.toString() +
			"\nPlease check your regular expression search string.",
			"Error", JOptionPane.ERROR_MESSAGE);
		} catch (IndexOutOfBoundsException ioobe) {
			// The user's regex replacement string referenced an
			// invalid group.
			JOptionPane.showMessageDialog(rtext,
			"Invalid group reference in replacement string:\n" +
			ioobe.getMessage(),
			"Error", JOptionPane.ERROR_MESSAGE);
		}

	}


}