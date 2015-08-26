/* 
 *  skatolo is a processing gui library.
 * 
 * Copyright (C)  2006-2012 by Andreas Schlegel
 * Copyright (C)  2015 by Jeremy Laviole
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 * 
 * 
 */
package fr.inria.skatolo.gui.controllers;

import fr.inria.skatolo.Skatolo;
import fr.inria.skatolo.gui.Controller;
import fr.inria.skatolo.gui.group.ControllerGroup;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import processing.core.PApplet;
import processing.core.PGraphics;

/**
 * A matrix is a 2d array with a pointer that traverses through the matrix in a timed interval. if an item of a matrix-column is active, the
 * x and y position of the corresponding cell will trigger an event and notify the program. see the skatolomatrix example for more
 * information.
 * 
 * @example controllers/skatolomatrix
 */
public class Matrix extends Controller<Matrix> {

	protected int cnt;

	protected int[][] _myCells;

	protected int stepX;

	protected int stepY;

	protected int cellX;

	protected int cellY;

	protected boolean isPressed;

	protected int _myCellX;

	protected int _myCellY;

	protected int sum;

	protected int _myInterval = 100;

	protected int currentX = -1;

	protected int currentY = -1;

	protected int _myMode = SINGLE_ROW;

	private Thread t;

	protected int gapX = 1;

	protected int gapY = 1;

	private Object _myPlug;

	private String _myPlugName;

	private boolean playing = true;

	private int bg = 0x00000000;

	/**
	 * Convenience constructor to extend Matrix.
	 * 
	 * @example use/skatoloextendController
	 * @param theskatolo
	 * @param theName
	 */
	public Matrix(Skatolo theskatolo, String theName) {
		this(theskatolo, theskatolo.getDefaultTab(), theName, 10, 10, 0, 0, 100, 100);
		theskatolo.register(theskatolo.getObjectForIntrospection(), theName, this);
	}

	public Matrix(Skatolo theskatolo, ControllerGroup<?> theParent, String theName, int theCellX, int theCellY, int theX, int theY, int theWidth, int theHeight) {
		super(theskatolo, theParent, theName, theX, theY, theWidth, theHeight);
		_myInterval = 100;
		setGrid(theCellX, theCellY);

		_myPlug = theskatolo.getObjectForIntrospection();
		_myPlugName = getName();
		_myCaptionLabel.align(Skatolo.LEFT, Skatolo.BOTTOM_OUTSIDE);
		_myCaptionLabel.setPadding(0, 4);
		runThread();
	}

	public Matrix setGrid(int theCellX, int theCellY) {
		_myCellX = theCellX;
		_myCellY = theCellY;
		sum = _myCellX * _myCellY;
		stepX = width / _myCellX;
		stepY = height / _myCellY;
		_myCells = new int[_myCellX][_myCellY];
		for (int x = 0; x < _myCellX; x++) {
			for (int y = 0; y < _myCellY; y++) {
				_myCells[x][y] = 0;
			}
		}
		return this;
	}

	/**
	 * set the speed of intervals in millis iterating through the matrix.
	 * 
	 * @param theInterval int
	 * @return Matrix
	 */

	public Matrix setInterval(int theInterval) {
		_myInterval = theInterval;
		return this;
	}

	public int getInterval() {
		return _myInterval;
	}

	public Matrix updateInternalEvents(PApplet theApplet) {
	// TODO: check this. 	
            setMouseOver(computeIsInside());

		if (getMouseOver()) {
			if (isPressed) {
				int tX = (int) ((getWindow().getPointerX() - position.x) / stepX);
				int tY = (int) ((getWindow().getPointerY() - position.y) / stepY);

				if (tX != currentX || tY != currentY) {
					tX = PApplet.min(PApplet.max(0, tX), _myCellX);
					tY = PApplet.min(PApplet.max(0, tY), _myCellY);
					boolean isMarkerActive = (_myCells[tX][tY] == 1) ? true : false;
					switch (_myMode) {
					default:
					case (SINGLE_COLUMN):
						for (int i = 0; i < _myCellY; i++) {
							_myCells[tX][i] = 0;
						}
						_myCells[tX][tY] = (!isMarkerActive) ? 1 : _myCells[tX][tY];
						break;
					case (SINGLE_ROW):
						for (int i = 0; i < _myCellY; i++) {
							_myCells[tX][i] = 0;
						}
						_myCells[tX][tY] = (!isMarkerActive) ? 1 : _myCells[tX][tY];
						break;
					case (MULTIPLES):
						_myCells[tX][tY] = (_myCells[tX][tY] == 1) ? 0 : 1;
						break;
					}
					currentX = tX;
					currentY = tY;
				}
			}
		}
		return this;
	}

	protected void onEnter() {
		isActive = true;
	}

	public void onLeave() {
		isActive = false;
	}

	public void mousePressed() {
		isActive = getMouseOver();
		if (getMouseOver()) {
			isPressed = true;
		}
	}

	protected void mouseReleasedOutside() {
		mouseReleased();
	}

	public void mouseReleased() {
		if (isActive) {
			isActive = false;
		}
		isPressed = false;
		currentX = -1;
		currentY = -1;
	}

	@Override public Matrix setValue(float theValue) {
		_myValue = theValue;
		broadcast(FLOAT);
		return this;
	}

	public Matrix play() {
		playing = true;
		return this;
	}

	public boolean isPlaying() {
		return playing;
	}

	public Matrix pause() {
		playing = false;
		return this;
	}

	public Matrix stop() {
		playing = false;
		cnt = 0;
		return this;
	}

	public Matrix trigger(int theColumn) {

		if (theColumn < 0 || theColumn >= _myCells.length) {
			return this;
		}

		for (int i = 0; i < _myCellY; i++) {
			if (_myCells[theColumn][i] == 1) {
				_myValue = 0;
				_myValue = (theColumn << 0) + (i << 8);
				setValue(_myValue);
				try {
					Method method = _myPlug.getClass().getMethod(_myPlugName, int.class, int.class);
					method.setAccessible(true);
					method.invoke(_myPlug, theColumn, i);
				} catch (SecurityException ex) {
					ex.printStackTrace();
				} catch (NoSuchMethodException ex) {
					ex.printStackTrace();
				} catch (IllegalArgumentException ex) {
					ex.printStackTrace();
				} catch (IllegalAccessException ex) {
					ex.printStackTrace();
				} catch (InvocationTargetException ex) {
					ex.printStackTrace();
				}
			}
		}
		return this;
	}

	@Override public Matrix update() {
		return setValue(_myValue);
	}

	public Matrix setGap(int theX, int theY) {
		gapX = theX;
		gapY = theY;
		return this;
	}

	public Matrix plugTo(Object theObject) {
		_myPlug = theObject;
		return this;
	}

	public Matrix plugTo(Object theObject, String thePlugName) {
		_myPlug = theObject;
		_myPlugName = thePlugName;
		return this;
	}

	/**
	 * set the state of a particular cell inside a matrix. use true or false for parameter theValue
	 * 
	 * @param theX
	 * @param theY
	 * @param theValue
	 * @return Matrix
	 */
	public Matrix set(int theX, int theY, boolean theValue) {
		_myCells[theX][theY] = (theValue == true) ? 1 : 0;
		return this;
	}

	public boolean get(int theX, int theY) {
		return _myCells[theX][theY] == 1 ? true : false;
	}

	public Matrix clear() {
		for (int x = 0; x < _myCells.length; x++) {
			for (int y = 0; y < _myCells[x].length; y++) {
				_myCells[x][y] = 0;
			}
		}
		return this;
	}

	public static int getX(int thePosition) {
		return ((thePosition >> 0) & 0xff);
	}

	public static int getY(int thePosition) {
		return ((thePosition >> 8) & 0xff);
	}

	public static int getX(float thePosition) {
		return (((int) thePosition >> 0) & 0xff);
	}

	public static int getY(float thePosition) {
		return (((int) thePosition >> 8) & 0xff);
	}

	public Matrix setCells(int[][] theCells) {
		setGrid(theCells.length, theCells[0].length);
		_myCells = theCells;
		return this;
	}

	public int[][] getCells() {
		return _myCells;
	}

	private void triggerEventFromThread() {
		if (playing) {
			cnt += 1;
			cnt %= _myCellX;
			trigger(cnt);
		}
	}

	private void runThread() {
		if (t == null) {
			t = new Thread(getName()) {

				public void run() {
					while (true) {
						triggerEventFromThread();
						try {
							sleep(_myInterval);
						} catch (InterruptedException e) {
							// throw new RuntimeException(e);
						}
					}
				}
			};
			t.start();
		}
	}

	@Override public void remove() {
		if (t != null) {
			t.interrupt();
		}
		super.remove();
	}

	/**
	 * use setMode to change the cell-activation which by default is skatolo.SINGLE_ROW, 1 active cell per row, but can be changed to
	 * skatolo.SINGLE_COLUMN or skatolo.MULTIPLES
	 * 
	 * @param theMode return Matrix
	 */
	public Matrix setMode(int theMode) {
		_myMode = theMode;
		return this;
	}

	public int getMode() {
		return _myMode;
	}

	public Matrix setBackground(int c) {
		bg = 0x00000000;
		if ((c >> 24 & 0xff) > 0) {
			bg = (c >> 24) << 24 | (c >> 16) << 16 | (c >> 8) << 8 | (c >> 0) << 0;
		}
		return this;
	}

	@Override public Matrix updateDisplayMode(int theMode) {
		_myDisplayMode = theMode;
		switch (theMode) {
		case (DEFAULT):
			_myControllerView = new MatrixView();
			break;
		case (IMAGE):
		case (SPRITE):
		case (CUSTOM):
		default:
			break;
		}
		return this;
	}

	class MatrixView implements ControllerView<Matrix> {

		public void display(PGraphics graphics, Matrix theController) {
			graphics.noStroke();
			graphics.fill(bg);
			graphics.rect(0, 0, width, height);
			for (int x = 0; x < _myCellX; x++) {
				for (int y = 0; y < _myCellY; y++) {

					if (_myCells[x][y] == 1) {
						graphics.fill(color.getActive());
						graphics.rect(x * stepX, y * stepY, stepX - gapX, stepY - gapY);
					} else {
						graphics.fill(color.getBackground());
						graphics.rect(x * stepX, y * stepY, stepX - gapX, stepY - gapY);
					}
				}
			}
			if (isMouseOver()) {
                            
                            // TODO: getMouseX() & getMouseY() here ? Why ?!
                            int x = (int) ((getWindow().getPointerX() - position.x) / stepX);
                            int y = (int) ((getWindow().getPointerY() - position.y) / stepY);
                            if (x >= 0 && x < _myCellX && y >= 0 && y < _myCellY) {
					graphics.fill(_myCells[x][y] == 1 ? color.getActive() : color.getForeground());
					graphics.rect(x * stepX, y * stepY, stepX - gapX, stepY - gapY);
                            }
			}
			graphics.fill(color.getActive());
			graphics.rect(cnt * stepX, 0, 1, height - gapY);
			if (isLabelVisible) {
				_myCaptionLabel.draw(graphics, 0, 0, theController);
			}
		}
	}
}