# Java Minesweeper Game - Classic Mine-Finding Strategy Game Implementation

This project is a complete implementation of the classic Minesweeper game in Java using Swing for the graphical user interface. The game provides an interactive 16x16 grid with 40 mines where players can reveal cells, mark potential mine locations, and experience the classic Minesweeper gameplay with a clean, modern interface.

The implementation features a robust game engine that handles mine placement, neighbor calculation, and recursive empty cell revelation. It includes a status bar that displays the number of remaining mines and game state (won/lost), mouse interaction support for both left-click (reveal) and right-click (mark) actions, and automatic game state management. The game follows the traditional Minesweeper rules where players must reveal all non-mine cells while avoiding mines to win.

## Repository Structure
```
ui-tests-starter/tstData/Hello/
├── catalog-info.yaml         # Backstage component definition file
└── src/com/zetcode/         # Main source code directory
    ├── Board.java           # Core game logic and UI rendering
    └── Minesweeper.java     # Main application entry point and window setup
```

## Usage Instructions
### Prerequisites
- Java Development Kit (JDK) 11 or higher
- Java Runtime Environment (JRE)
- Java Swing library (included in JDK)

### Installation
1. Clone the repository:
```bash
git clone <repository-url>
cd ui-tests-starter/tstData/Hello
```

2. Compile the Java files:
```bash
javac src/com/zetcode/*.java
```

### Quick Start
1. Run the compiled game:
```bash
java -cp src com.zetcode.Minesweeper
```

2. Play the game:
- Left-click to reveal a cell
- Right-click to mark/unmark a potential mine
- The status bar shows remaining mines or game status
- Reveal all non-mine cells to win

### More Detailed Examples
#### Game Controls
```java
// Left-click to reveal a cell
mousePressed(MouseEvent e) {
    if (e.getButton() == MouseEvent.BUTTON1) {
        // Reveals the cell
    }
}

// Right-click to mark a cell
mousePressed(MouseEvent e) {
    if (e.getButton() == MouseEvent.BUTTON3) {
        // Marks/unmarks the cell as a potential mine
    }
}
```

### Troubleshooting
#### Common Issues
1. Missing Images Error
- Problem: Game fails to load cell images
- Solution: Ensure the image resources are in the correct path: `src/resources/`
- Required files: 0.png through 12.png for different cell states

2. Game Window Not Appearing
- Check if Java Swing is properly initialized
- Verify the main class is being executed correctly
- Ensure no other processes are blocking the window creation

#### Performance Optimization
- The game is optimized for a 16x16 grid with 40 mines
- Rendering occurs only when necessary through selective repainting
- Cell revelation uses efficient recursive algorithms for empty cells

## Data Flow
The game processes user input through mouse events, updates the game state matrix, and renders the updated board state to the screen.

```ascii
[User Input] -> [Mouse Event Handler] -> [Game State Update] -> [Board Rendering]
     ^                                          |                      |
     |                                         |                      |
     +----------------------------------------+----------------------+
                     Game Loop Feedback
```

Key Component Interactions:
1. Minesweeper class initializes the main window and status bar
2. Board class manages the game state and handles user input
3. Mouse events trigger cell revelation or marking
4. Game state updates propagate through the board matrix
5. Status bar displays game progress and results
6. Graphics system renders the updated board state
7. Cell state changes trigger recursive updates for empty cells
