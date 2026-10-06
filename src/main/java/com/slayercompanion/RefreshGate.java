package com.slayercompanion;

/**
 * Holds back sidebar refreshes while one of its dropdowns is open. A refresh can come with every kill. Most
 * only change text in place, but a rebuild would close the open list under the cursor, so a refresh asked
 * for while a list is open runs once, after the list closes. Swing thread only.
 */
final class RefreshGate
{
	private boolean pending;

	/** Whether to rebuild now. A refresh while a list is open is remembered instead. */
	boolean shouldRebuild(boolean popupOpen)
	{
		if (popupOpen)
		{
			pending = true;
			return false;
		}
		pending = false;
		return true;
	}

	/** A list closed. Whether a refresh held back while it was open should run now. */
	boolean popupClosed()
	{
		final boolean run = pending;
		pending = false;
		return run;
	}
}
