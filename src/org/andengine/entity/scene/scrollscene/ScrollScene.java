package org.andengine.entity.scene.scrollscene;

import org.andengine.entity.IEntity;
import org.andengine.entity.modifier.MoveXModifier;
import org.andengine.entity.scene.IOnSceneTouchListener;
import org.andengine.entity.scene.Scene;
import org.andengine.entity.shape.IAreaShape;
import org.andengine.input.touch.TouchEvent;
import org.andengine.util.adt.list.SmartList;
import org.andengine.util.modifier.IModifier;
import org.andengine.util.modifier.IModifier.IModifierListener;
import org.andengine.util.modifier.ease.EaseLinear;
import org.andengine.util.modifier.ease.IEaseFunction;

/**
 * This is an implementation of cocos2d-x's <a href=
 * "https://github.com/cocos2d/cocos2d-x-extensions/tree/master/extensions/CCScrollLayer"
 * >CCScrollLayer</a> for AndEngine.
 * 
 * @author korn3l
 * @since Nov 26, 2012
 */

public class ScrollScene extends Scene implements IOnSceneTouchListener {
	// ===========================================================
	// Constants
	// ===========================================================
	private static final float SLIDE_DURATION_DEFAULT = 0.3f;
	private static final float MINIMUM_TOUCH_LENGTH_TO_SLIDE_DEFAULT = 30f;
	private static final float MINIMUM_TOUCH_LENGTH_TO_CHANGE_PAGE_DEFAULT = 100f;

	// ===========================================================
	// Fields
	// ===========================================================

	// XXX is this really necessary since the scene has a list of children ?
	private SmartList<IAreaShape> mPages = new SmartList<IAreaShape>();
	private ScrollState mState;
	private IOnScrollScenePageListener mOnScrollScenePageListener;

	private int mCurrentPage;
	private float mStartSwipe;

	private float mMinimumTouchLengthToSlide;
	private float mMinimumTouchLengthToChagePage;

	private float lastX;

	private float mPageWidth;
	private float mPageHeight;
	private float mOffset;

	private IEaseFunction mEaseFunction;
	private MoveXModifier mMoveXModifier;
	private IModifierListener<IEntity> mMoveXModifierListener;
	private boolean mEaseFunctionDirty = false;

	// ===========================================================
	// Constructors
	// ===========================================================

	public ScrollScene() {
		this(0, 0, MINIMUM_TOUCH_LENGTH_TO_SLIDE_DEFAULT, MINIMUM_TOUCH_LENGTH_TO_CHANGE_PAGE_DEFAULT);
	}

	public ScrollScene(final float pPageWidth, final float pPageHeight) {
		this(pPageWidth, pPageHeight, MINIMUM_TOUCH_LENGTH_TO_SLIDE_DEFAULT, MINIMUM_TOUCH_LENGTH_TO_CHANGE_PAGE_DEFAULT);
	}

	public ScrollScene(final float pPageWidth, final float pPageHeight, float pMinimumTouchLengthToSlide, float pMinimumTouchLengthToChangePage) {
		this.mPageWidth = pPageWidth;
		this.mPageHeight = pPageHeight;

		this.mMinimumTouchLengthToSlide = pMinimumTouchLengthToSlide;
		this.mMinimumTouchLengthToChagePage = pMinimumTouchLengthToChangePage;
		this.mEaseFunction = EaseLinear.getInstance();

		this.setOnSceneTouchListener(this);
		this.mCurrentPage = 0;
	}

	// ===========================================================
	// Getter & Setter
	// ===========================================================

	public int getPagesCount() {
		return this.mPages.size();
	}

	public float getPageWidth() {
		return this.mPageWidth;
	}

	public float getPageHeight() {
		return this.mPageHeight;
	}

	public void registerScrollScenePageListener(IOnScrollScenePageListener pOnScrollScenePageListener) {
		this.mOnScrollScenePageListener = pOnScrollScenePageListener;
	}

	public void setEaseFunction(IEaseFunction pEaseFunction) {
		this.mEaseFunction = pEaseFunction;
		this.mEaseFunctionDirty = true;
	}

	public void setPageWidth(float pageWidth) {
		this.mPageWidth = pageWidth;
	}

	public void setPageHeight(float pageHeight) {
		this.mPageHeight = pageHeight;
	}

	public void setOffset(float offset) {
		this.mOffset = offset;
	}

	public void setMinimumLengthToSlide(float pMinimumTouchLengthToSlide) {
		this.mMinimumTouchLengthToSlide = pMinimumTouchLengthToSlide;
	}

	public void setMinimumLengthToChangePage(float pMinimumTouchLengthToChangePage) {
		this.mMinimumTouchLengthToChagePage = pMinimumTouchLengthToChangePage;
	}

	public boolean isFirstPage(IAreaShape pPage) {
		if (this.mPages.getFirst().equals(pPage)) {
			return true;
		}
		return false;
	}

	public boolean isLastPage(IAreaShape pPage) {
		if (this.mPages.getLast().equals(pPage)) {
			return true;
		}
		return false;
	}

	public IAreaShape getCurrentPage() {
		return this.mPages.get(this.mCurrentPage);
	}

	public int getCurrentPageNumber() {
		return this.mCurrentPage;
	}

	// ===========================================================
	// Methods for/from SuperClass/Interfaces
	// ===========================================================

	@Override
	public boolean onSceneTouchEvent(final Scene pScene, final TouchEvent pSceneTouchEvent) {
		final float touchX = pSceneTouchEvent.getX();
		switch (pSceneTouchEvent.getAction()) {
		case TouchEvent.ACTION_DOWN:
			this.mStartSwipe = touchX;
			this.lastX = this.getX();
			this.mState = ScrollState.IDLE;
			return true;
		case TouchEvent.ACTION_MOVE:
			if (this.mState != ScrollState.SLIDING && Math.abs(touchX - mStartSwipe) >= this.mMinimumTouchLengthToSlide) {
				this.mState = ScrollState.SLIDING;
				// Avoid jerk after state change.
				this.mStartSwipe = touchX;
				return true;
			} else if (this.mState == ScrollState.SLIDING) {
				float offsetX = touchX - mStartSwipe;
				this.setX(lastX + offsetX);
				return true;
			} else {
				return false;
			}
		case TouchEvent.ACTION_UP:
		case TouchEvent.ACTION_CANCEL:
			if (this.mState == ScrollState.SLIDING) {
				int selectedPage = this.mCurrentPage;
				float delta = touchX - mStartSwipe;
				if (Math.abs(delta) >= this.mMinimumTouchLengthToChagePage) {
					if (delta < 0.f && selectedPage < this.mPages.size() - 1)
						selectedPage++;
					else if (delta > 0.f && selectedPage > 0)
						selectedPage--;
				}
				moveToPage(selectedPage);
			}
			return true;
		default:
			return false;
		}
	}

	// ===========================================================
	// Methods
	// ===========================================================

	/**
	 * Updates all pages positions & adds them as children if needed.<br>
	 * Can be used to update position of pages after screen reshape, or for rearranging pages after
	 * insertion or removal of pages.
	 */
	public void updatePages() {
		int i = 0;
		for (IAreaShape page : mPages) {
			page.setPosition(i * (this.mPageWidth - this.mOffset), 0);
			i++;
		}
	}

	/**
	 * Adds new page to the right end of the scroll scene.
	 * 
	 * @param pPage
	 */
	public void addPage(final IAreaShape pPage) {

		this.mPages.add(pPage);
		this.attachChild(pPage);

		this.updatePages();

	}

	/**
	 * Adds new page and reorders pages trying to set given number for newly added page. <br>
	 * If number > page count -> adds new page to the right end of the scroll scene.<br>
	 * If number <= 0 -> adds new page to the left end of the scroll scene.
	 */
	public void addPage(final IAreaShape pPage, final int pPageNumber) {

		this.mPages.add(pPageNumber, pPage);
		this.attachChild(pPage);

		this.updatePages();
	}

	/**
	 * Removes page if it's one of scroll scene pages (not children) Does nothing if page not found.
	 */
	public void removePage(final IAreaShape pPage) {
		this.unregisterTouchArea(pPage);
		this.detachChild(pPage);
		this.mPages.remove(pPage);

		updatePages();

		this.mCurrentPage = Math.min(this.mCurrentPage, mPages.size() - 1);
		this.moveToPage(this.mCurrentPage);

	}

	/** Removes page with given number. Does nothing if there's no page for such number. */
	void removePageWithNumber(final IAreaShape pPage, final int pPageNumber) {
		if (pPageNumber < this.mPages.size())
			this.removePage(this.mPages.get(pPageNumber));
	}

	/**
	 * Moves the scene to the page with the given number and, if it has a OnScrollScenePageListener
	 * registered, calls {@link #onMoveToPageStarted()} when started and
	 * {@link #onMoveToPageFinished()} method when finished.
	 * 
	 * @param pageNumber
	 * @throws IndexOutOfBoundsException
	 *             if number >= totalPages or < 0.
	 */
	private void moveToPage(final int pageNumber) {

		if (pageNumber >= this.mPages.size()) {
			throw new IndexOutOfBoundsException("moveToPage: " + pageNumber + " - wrong page number, out of bounds.");
		}

		this.mCurrentPage = pageNumber;

		final float toX = positionForPageWithNumber(pageNumber);

		if (this.mEaseFunctionDirty) {
			if (this.mMoveXModifier != null) {
				if (this.mMoveXModifierListener != null)
					this.mMoveXModifier.removeModifierListener(this.mMoveXModifierListener);
				this.unregisterEntityModifier(mMoveXModifier);
				this.mMoveXModifierListener = null;
				this.mMoveXModifier = null;
			}
			this.mEaseFunctionDirty = false;
		}

		if (this.mMoveXModifier == null) {
			this.mMoveXModifier = new MoveXModifier(SLIDE_DURATION_DEFAULT, this.getX(), toX, this.mEaseFunction);
			this.mMoveXModifier.setAutoUnregisterWhenFinished(false);
			this.registerEntityModifier(this.mMoveXModifier);
		} else {
			this.mMoveXModifier.reset(SLIDE_DURATION_DEFAULT, this.getX(), toX);
		}

		if (mOnScrollScenePageListener != null) {
			if (this.mMoveXModifierListener == null) {
				this.mMoveXModifierListener = new IModifierListener<IEntity>() {
					@Override
					public void onModifierStarted(IModifier<IEntity> pModifier, IEntity pItem) {
						ScrollScene.this.mOnScrollScenePageListener.onMoveToPageStarted(ScrollScene.this.mCurrentPage);
					}

					@Override
					public void onModifierFinished(IModifier<IEntity> pModifier, IEntity pItem) {
						ScrollScene.this.mOnScrollScenePageListener.onMoveToPageFinished(ScrollScene.this.mCurrentPage);
					}
				};
				this.mMoveXModifier.addModifierListener(this.mMoveXModifierListener);
			}
		}

	}

	/**
	 * Immediately moves the scene to the given page number.
	 * 
	 * @param pageNumber
	 * @throws IndexOutOfBoundsException
	 *             if number >= totalPages or < 0.
	 */
	public void selectPage(int pageNumber) {
		if (pageNumber >= this.mPages.size()) {
			throw new IndexOutOfBoundsException("selectPage: " + pageNumber + " - wrong page number, out of bounds.");
		}

		this.setX(positionForPageWithNumber(pageNumber));
		this.mCurrentPage = pageNumber;
	}

	/**
	 * @param pageNumber
	 * @return the position of the page with the given number
	 */
	public float positionForPageWithNumber(int pageNumber) {
		return pageNumber * (this.mPageWidth - this.mOffset) * -1f;
	}

	/**
	 * 
	 * @param pPosition
	 *            meaning the X value
	 * @return the number of the page at the given position
	 */
	public int pageNumberForPosition(float pPosition) {
		float pageFloat = -pPosition / (this.mPageWidth - this.mOffset);
		int pageNumber = (int) Math.ceil(pageFloat);

		if ((float) pageNumber - pageFloat >= 0.5f)
			pageNumber--;

		pageNumber = Math.max(0, pageNumber);
		pageNumber = Math.min(this.mPages.size() - 1, pageNumber);

		return pageNumber;
	}

	/**
	 * Moves to the next page. Does nothing if the current page is the last page
	 */
	public void moveToNextPage() {
		final int pageNo = this.mPages.size();

		if (this.mCurrentPage + 1 < pageNo)
			this.moveToPage(this.mCurrentPage + 1);
	}

	/**
	 * Moves to the previous page. Does nothing if the current page is the first page
	 */
	public void moveToPreviousPage() {
		if (this.mCurrentPage > 0)
			this.moveToPage(this.mCurrentPage - 1);
	}

	/**
	 * Removes all Pages from the scene
	 */
	public void clearPages() {
		for (int i = this.mPages.size() - 1; i >= 0; i--) {
			final IAreaShape page = this.mPages.remove(i);
			this.detachChild(page);
			this.unregisterTouchArea(page);
		}
	}

	// ===========================================================
	// Inner and Anonymous Classes
	// ===========================================================
	public static interface IOnScrollScenePageListener {
		// ===========================================================
		// Constants
		// ===========================================================

		// ===========================================================
		// Methods
		// ===========================================================
		public void onMoveToPageStarted(final int pPageNumber);

		public void onMoveToPageFinished(final int pPageNumber);
	}

	private enum ScrollState {
		IDLE, SLIDING;
	}

}
